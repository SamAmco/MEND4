package co.samco.mendroid.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.samco.mendroid.R
import co.samco.mendroid.model.ErrorToastManager
import co.samco.mendroid.model.FileEventManager
import co.samco.mendroid.model.PrivateKeyManager
import co.samco.mendroid.model.PropertyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DecryptViewModel @Inject constructor(
    private val propertyManager: PropertyManager,
    private val privateKeyManager: PrivateKeyManager,
    private val errorToastManager: ErrorToastManager,
    private val fileEventManager: FileEventManager,
    application: Application
) : AndroidViewModel(application) {

    var searchText by mutableStateOf(TextFieldValue(""))

    private val fileHelper = FileHelper(application, propertyManager, fileEventManager)

    val availableLogs = fileHelper.logFileNames
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun onLogSelected(log: LogFileData) {
        val inputStream = getApplication<Application>().contentResolver.openInputStream(log.uri)
        if (inputStream != null) inputStream.use { privateKeyManager.decryptLog(it) }
        else errorToastManager.showErrorToast(R.string.error_opening_log)
    }

    val logLines = combine(
        privateKeyManager.decryptedLogLines,
        snapshotFlow { searchText }
    ) { lines, search ->
        val ignoreCase = search.text.none { it.isUpperCase() }
        if (search.text.isEmpty()) lines
        else lines.filter { it.contains(search.text, ignoreCase = ignoreCase) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val decryptingLog = privateKeyManager.decryptingLog
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
}