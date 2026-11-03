package co.samco.mendroid.viewmodel

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.samco.mendroid.R
import co.samco.mendroid.model.ErrorToastManager
import co.samco.mendroid.model.LogFileData
import co.samco.mendroid.model.LogFileManager
import co.samco.mendroid.model.PrivateKeyManager
import co.samco.mendroid.nextcloud.NextcloudException
import co.samco.mendroid.nextcloud.NextcloudFailure
import co.samco.mendroid.nextcloud.RemotePath
import co.samco.mendroid.remote.NextcloudReadFiles
import co.samco.mendroid.remote.RemoteReadFile
import co.samco.mendroid.remote.RemoteReadProblem
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DecryptLogItem {
    val name: String
    data class Local(val file: LogFileData) : DecryptLogItem { override val name get() = file.name }
    data class Nextcloud(val file: RemoteReadFile) : DecryptLogItem { override val name get() = file.name }
}
data class DecryptLogListState(
    val logs: List<DecryptLogItem> = emptyList(),
    val loading: Boolean = false,
    val opening: Boolean = false,
    val problems: List<RemoteReadProblem> = emptyList()
)

@HiltViewModel
class SelectLogViewModel @Inject constructor(
    private val privateKeyManager: PrivateKeyManager,
    private val errorToastManager: ErrorToastManager,
    private val logFileManager: LogFileManager,
    private val remote: NextcloudReadFiles,
    application: Application
) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(DecryptLogListState())
    val state = mutableState.asStateFlow()
    private var refreshJob: Job? = null
    private var openJob: Job? = null
    private var generation = 0
    private var openGeneration = 0

    init {
        viewModelScope.launch {
            combine(privateKeyManager.unlocked, remote.config) { unlocked, config ->
                Triple(unlocked, config.account?.id, config.additionalFolders)
            }.distinctUntilChanged().collect { (unlocked, _, _) ->
                if (unlocked) refreshLogs()
                else {
                    generation++
                    refreshJob?.cancel()
                    openGeneration++
                    openJob?.cancel()
                    mutableState.value = DecryptLogListState()
                }
            }
        }
    }

    fun refreshLogs() {
        if (!privateKeyManager.unlocked.value) return
        refreshJob?.cancel()
        val request = ++generation
        mutableState.update { it.copy(loading = true, problems = emptyList()) }
        refreshJob = viewModelScope.launch {
            try {
                val local = try {
                    logFileManager.refresh()
                    logFileManager.logs.first().map { DecryptLogItem.Local(it) }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { errorToastManager.showErrorToast(R.string.log_dir_error); emptyList() }
                if (request != generation) return@launch
                mutableState.update { it.copy(logs = local) }
                val listing = remote.logs()
                if (request == generation && privateKeyManager.unlocked.value) mutableState.update {
                    it.copy(logs = local + listing.files.map(DecryptLogItem::Nextcloud), problems = listing.problems)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: IOException) {
                if (request == generation) mutableState.update { it.copy(problems = listOf(RemoteReadProblem(
                    RemotePath.ROOT, (e as? NextcloudException)?.reason ?: NextcloudFailure.CONNECTION))) }
            } finally {
                if (request == generation) mutableState.update { it.copy(loading = false) }
            }
        }
    }

    fun cancelOpen() { openGeneration++; openJob?.cancel(); mutableState.update { it.copy(opening = false) } }

    fun onLogSelected(log: DecryptLogItem, onOpened: () -> Unit) {
        if (!privateKeyManager.unlocked.value || mutableState.value.opening) return
        val request = ++openGeneration
        mutableState.update { it.copy(opening = true) }
        openJob = viewModelScope.launch {
            try {
                val uri: Uri = when (log) {
                    is DecryptLogItem.Local -> {
                        logFileManager.refresh()
                        logFileManager.logs.first().firstOrNull { it.uri == log.file.uri }?.uri
                            ?: throw IOException("Selected log is no longer in the directory")
                    }
                    is DecryptLogItem.Nextcloud -> remote.open(log.file).toUri()
                }
                withContext(Dispatchers.IO) {
                    val input = getApplication<Application>().contentResolver.openInputStream(uri) ?: throw IOException("Cannot open log")
                    input.use {
                        if (!privateKeyManager.unlocked.value) throw CancellationException("MEND locked")
                        withContext(Dispatchers.Main) {
                            if (request != openGeneration || !privateKeyManager.unlocked.value) throw CancellationException("MEND locked or opening cancelled")
                            onOpened()
                        }
                        privateKeyManager.decryptLog(it)
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { errorToastManager.showErrorToast(R.string.remote_read_error) }
            finally { if (request == openGeneration) mutableState.update { it.copy(opening = false) } }
        }
    }
}
