package co.samco.mendroid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.samco.mendroid.nextcloud.NextcloudConfig
import co.samco.mendroid.nextcloud.NextcloudException
import co.samco.mendroid.nextcloud.NextcloudFailure
import co.samco.mendroid.nextcloud.NextcloudLogin
import co.samco.mendroid.nextcloud.RemoteFolder
import co.samco.mendroid.nextcloud.RemotePath
import co.samco.mendroid.upload.BackupService
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

enum class NextcloudUiProblem { INVALID_SETUP, OPERATION_FAILED }
sealed interface NextcloudUiError {
    data class Backup(val reason: UploadFailure) : NextcloudUiError
    data class Nextcloud(val reason: NextcloudFailure) : NextcloudUiError
    data class Operation(val problem: NextcloudUiProblem) : NextcloudUiError
}
enum class NextcloudFolderUse { ATTACHMENTS, LOGS, ADDITIONAL }
data class FolderPickerState(
    val use: NextcloudFolderUse,
    val path: RemotePath,
    val folders: List<RemoteFolder> = emptyList(),
    val loading: Boolean = true,
    val error: NextcloudUiError? = null
)
data class UploadActions(
    val signingIn: Boolean = false,
    val importing: Boolean = false,
    val picker: FolderPickerState? = null,
    val error: NextcloudUiError? = null,
    val imported: Boolean = false
)
data class NextcloudUiState(
    val config: NextcloudConfig = NextcloudConfig(),
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
    fun browserFailed() { cancelLogin(); actions.update { it.copy(error = NextcloudUiError.Operation(NextcloudUiProblem.OPERATION_FAILED)) } }
    fun disconnect() { cancelLogin(); closeFolderPicker(); command { backups.disconnect() } }
    fun pause(value: Boolean) = command { backups.pause(value) }
    fun mobileData(value: Boolean) = command { backups.allowMobileData(value) }
    fun retry() = command { backups.retry() }
    fun forget(entry: UploadEntry) = command { backups.forget(entry) }
    fun backupVerified(entry: UploadEntry) = command { backups.backupVerified(entry) }

    fun browse(use: NextcloudFolderUse, path: RemotePath = RemotePath.ROOT) {
        browseJob?.cancel()
        val generation = ++browseGeneration
        actions.update { it.copy(picker = FolderPickerState(use, path)) }
        browseJob = viewModelScope.launch {
            try {
                val account = backups.config.value.account?.id
                val result = backups.folders(path)
                if (generation == browseGeneration) {
                    val picker = if (backups.config.value.account?.id == account) FolderPickerState(use, path, result, loading = false) else null
                    actions.update { it.copy(picker = picker) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == browseGeneration) actions.update {
                    it.copy(picker = FolderPickerState(use, path, loading = false, error = error(e)))
                }
            }
        }
    }

    fun selectFolder() {
        val picker = actions.value.picker ?: return
        if (picker.loading || picker.error != null) return
        command {
            when (picker.use) {
                NextcloudFolderUse.ATTACHMENTS -> backups.selectFolder(UploadKind.ATTACHMENT, picker.path)
                NextcloudFolderUse.LOGS -> backups.selectFolder(UploadKind.LOG, picker.path)
                NextcloudFolderUse.ADDITIONAL -> backups.addAdditionalFolder(picker.path)
            }
            closeFolderPicker()
        }
    }
    fun closeFolderPicker() {
        browseGeneration++
        browseJob?.cancel()
        actions.update { it.copy(picker = null) }
    }

    fun removeAdditionalFolder(path: RemotePath) = command { backups.removeAdditionalFolder(path) }

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

    private fun error(exception: Exception): NextcloudUiError = when (exception) {
        is NextcloudException -> NextcloudUiError.Nextcloud(exception.reason)
        is UploadException -> NextcloudUiError.Backup(exception.reason)
        is IOException -> NextcloudUiError.Nextcloud(NextcloudFailure.CONNECTION)
        is IllegalArgumentException -> NextcloudUiError.Operation(NextcloudUiProblem.INVALID_SETUP)
        else -> NextcloudUiError.Operation(NextcloudUiProblem.OPERATION_FAILED)
    }
}
