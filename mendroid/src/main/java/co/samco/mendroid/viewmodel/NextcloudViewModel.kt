package co.samco.mendroid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.samco.mendroid.upload.BackupService
import co.samco.mendroid.upload.NextcloudLogin
import co.samco.mendroid.upload.RemoteFolder
import co.samco.mendroid.upload.RemotePath
import co.samco.mendroid.upload.UploadConfig
import co.samco.mendroid.upload.UploadEntry
import co.samco.mendroid.upload.UploadException
import co.samco.mendroid.upload.UploadFailure
import co.samco.mendroid.upload.UploadKind
import co.samco.mendroid.upload.UploadProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class UploadUiProblem { INVALID_SETUP, OPERATION_FAILED }
sealed interface UploadUiError {
    data class Backup(val reason: UploadFailure) : UploadUiError
    data class Operation(val problem: UploadUiProblem) : UploadUiError
}
data class FolderPickerState(
    val kind: UploadKind,
    val path: RemotePath,
    val folders: List<RemoteFolder> = emptyList(),
    val loading: Boolean = true,
    val error: UploadUiError? = null
)
data class UploadActions(
    val signingIn: Boolean = false,
    val importing: Boolean = false,
    val picker: FolderPickerState? = null,
    val error: UploadUiError? = null,
    val imported: Boolean = false
)
data class NextcloudUiState(
    val config: UploadConfig = UploadConfig(),
    val entries: List<UploadEntry> = emptyList(),
    val progress: UploadProgress? = null,
    val actions: UploadActions = UploadActions()
)
sealed interface NextcloudEffect {
    data class OpenBrowser(val url: String) : NextcloudEffect
}

@HiltViewModel
class NextcloudViewModel @Inject constructor(
    private val backups: BackupService,
    private val login: NextcloudLogin
) : ViewModel() {
    private val actions = MutableStateFlow(UploadActions())
    val state = combine(backups.config, backups.entries, backups.progress, actions, ::NextcloudUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, NextcloudUiState(config = backups.config.value))
    private val effectsChannel = Channel<NextcloudEffect>(Channel.BUFFERED)
    val effects = effectsChannel.receiveAsFlow()
    private var loginJob: Job? = null
    private var loginGeneration = 0
    private var browseJob: Job? = null
    private var browseGeneration = 0

    init { command { backups.refresh() } }

    fun connect(server: String) {
        cancelLogin()
        val generation = loginGeneration
        actions.update { it.copy(signingIn = true, error = null) }
        loginJob = viewModelScope.launch {
            try {
                val request = login.begin(server)
                effectsChannel.send(NextcloudEffect.OpenBrowser(request.browserUrl))
                val session = login.awaitSession(request)
                if (generation == loginGeneration) backups.connect(session)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == loginGeneration) actions.update { it.copy(error = error(e)) }
            } finally {
                if (generation == loginGeneration) actions.update { it.copy(signingIn = false) }
            }
        }
    }

    fun cancelLogin() {
        loginGeneration++
        loginJob?.cancel()
        actions.update { it.copy(signingIn = false) }
    }
    fun browserFailed() { cancelLogin(); actions.update { it.copy(error = UploadUiError.Operation(UploadUiProblem.OPERATION_FAILED)) } }
    fun disconnect() { cancelLogin(); closeFolderPicker(); command { backups.disconnect() } }
    fun pause(value: Boolean) = command { backups.pause(value) }
    fun mobileData(value: Boolean) = command { backups.allowMobileData(value) }
    fun retry() = command { backups.retry() }
    fun forget(entry: UploadEntry) = command { backups.forget(entry) }
    fun backupVerified(entry: UploadEntry) = command { backups.backupVerified(entry) }

    fun browse(kind: UploadKind, path: RemotePath = RemotePath.ROOT) {
        browseJob?.cancel()
        val generation = ++browseGeneration
        actions.update { it.copy(picker = FolderPickerState(kind, path)) }
        browseJob = viewModelScope.launch {
            try {
                val account = backups.config.value.account?.id
                val result = backups.folders(path)
                if (generation == browseGeneration) {
                    val picker = if (backups.config.value.account?.id == account) FolderPickerState(kind, path, result, loading = false) else null
                    actions.update { it.copy(picker = picker) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == browseGeneration) actions.update {
                    it.copy(picker = FolderPickerState(kind, path, loading = false, error = error(e)))
                }
            }
        }
    }

    fun selectFolder() {
        val picker = actions.value.picker ?: return
        if (picker.loading || picker.error != null) return
        command { backups.selectFolder(picker.kind, picker.path); closeFolderPicker() }
    }
    fun closeFolderPicker() {
        browseGeneration++
        browseJob?.cancel()
        actions.update { it.copy(picker = null) }
    }

    fun backupExisting() {
        if (actions.value.importing) return
        actions.update { it.copy(importing = true, imported = false, error = null) }
        viewModelScope.launch {
            try {
                backups.backupExisting()
                actions.update { it.copy(imported = true) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { actions.update { it.copy(error = error(e)) } }
            finally { actions.update { it.copy(importing = false) } }
        }
    }

    private fun command(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { actions.update { it.copy(error = error(e)) } }
        }
    }

    private fun error(exception: Exception): UploadUiError = when (exception) {
        is UploadException -> UploadUiError.Backup(exception.reason)
        is IOException -> UploadUiError.Backup(UploadFailure.CONNECTION)
        is IllegalArgumentException -> UploadUiError.Operation(UploadUiProblem.INVALID_SETUP)
        else -> UploadUiError.Operation(UploadUiProblem.OPERATION_FAILED)
    }
}
