package co.samco.mendroid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.samco.mendroid.R
import co.samco.mendroid.model.ErrorToastManager
import co.samco.mendroid.model.LogFileData
import co.samco.mendroid.model.LogFileManager
import co.samco.mendroid.model.PrivateKeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SelectLogViewModel @Inject constructor(
    private val privateKeyManager: PrivateKeyManager,
    private val errorToastManager: ErrorToastManager,
    private val logFileManager: LogFileManager,
    application: Application
) : AndroidViewModel(application) {

    val availableLogs = logFileManager.logs
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun refreshLogs() {
        viewModelScope.launch {
            try {
                logFileManager.refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                errorToastManager.showErrorToast(R.string.log_dir_error)
            }
        }
    }

    fun onLogSelected(log: LogFileData, onOpened: () -> Unit) {
        viewModelScope.launch {
            try {
                logFileManager.refresh()
                val current = logFileManager.logs.first().firstOrNull { it.uri == log.uri }
                    ?: throw IOException("Selected log is no longer in the directory")
                withContext(Dispatchers.IO) {
                    val input = getApplication<Application>().contentResolver.openInputStream(current.uri)
                        ?: throw IOException("Cannot open log")
                    input.use {
                        withContext(Dispatchers.Main) { onOpened() }
                        privateKeyManager.decryptLog(it)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                errorToastManager.showErrorToast(R.string.error_opening_log)
            }
        }
    }
}
