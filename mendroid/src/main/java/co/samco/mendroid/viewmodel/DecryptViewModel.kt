package co.samco.mendroid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.samco.mendroid.R
import co.samco.mendroid.model.ErrorToastManager
import co.samco.mendroid.model.PrivateKeyManager
import co.samco.mendroid.model.PropertyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DecryptViewModel @Inject constructor(
    private val propertyManager: PropertyManager,
    private val privateKeyManager: PrivateKeyManager,
    private val errorToastManager: ErrorToastManager,
    application: Application
) : AndroidViewModel(application) {
    private val fileHelper = FileHelper(application, propertyManager)

    val availableLogs = fileHelper.logFileNames
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun onLogSelected(log: LogFileData) {
        val inputStream = getApplication<Application>().contentResolver.openInputStream(log.uri)
        if (inputStream != null) inputStream.use { privateKeyManager.decryptLog(it) }
        else errorToastManager.showErrorToast(R.string.error_opening_log)
    }

    val logLines = privateKeyManager.decryptedLogLines
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val decryptingLog = privateKeyManager.decryptingLog
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
}