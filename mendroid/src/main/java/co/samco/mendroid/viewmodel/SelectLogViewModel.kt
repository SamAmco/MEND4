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

    val availableLogs = logFileManager.knownLogs
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun onLogSelected(log: LogFileData) {
        viewModelScope.launch {
            val inputStream = getApplication<Application>().contentResolver.openInputStream(log.uri)
            if (inputStream != null) inputStream.use { privateKeyManager.decryptLog(it) }
            else errorToastManager.showErrorToast(R.string.error_opening_log)
        }
    }
}

