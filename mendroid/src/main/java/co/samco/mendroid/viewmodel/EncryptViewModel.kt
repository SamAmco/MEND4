package co.samco.mendroid.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.samco.mend4.core.AppProperties
import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.core.util.LogUtils
import co.samco.mendroid.BuildConfig
import co.samco.mendroid.model.ErrorToastManager
import co.samco.mendroid.model.PropertyManager
import co.samco.mendroid.R
import co.samco.mendroid.model.LogFileData
import co.samco.mendroid.model.LogFileManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class EncryptViewModel @Inject constructor(
    application: Application,
    private val cryptoProvider: CryptoProvider,
    private val propertyManager: PropertyManager,
    private val errorToastManager: ErrorToastManager,
    private val logFileManager: LogFileManager
) : AndroidViewModel(application) {

    private val context get() = this.getApplication<Application>().applicationContext

    var currentEntryText by mutableStateOf(TextFieldValue(""))

    val knownLogs = logFileManager.knownLogs
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _showSelectLogDialog = MutableStateFlow(false)
    val showSelectLogDialog: StateFlow<Boolean> = _showSelectLogDialog

    private val currentLog = logFileManager.currentLog
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val currentLogName: StateFlow<String?> = currentLog.map { it?.name }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun encryptText() {
        viewModelScope.launch {
            val currLog = currentLog.value
            if (currLog == null) {
                errorToastManager.showErrorToast(R.string.could_not_find_current_log)
                return@launch
            }

            val outputStream = context.contentResolver.openOutputStream(currLog.uri, "wa")
            if (outputStream == null) {
                errorToastManager.showErrorToast(R.string.failed_to_write_to_log_file)
                return@launch
            }

            val logText = LogUtils.addHeaderToLogText(
                currentEntryText.text,
                "ANDROID",
                BuildConfig.VERSION_NAME,
                "\n"
            )

            cryptoProvider.encryptLogStream(logText, outputStream)

            currentEntryText = TextFieldValue("")
        }
    }

    fun encryptFile(uri: Uri?) {
        viewModelScope.launch {
            if (uri == null) {
                errorToastManager.showErrorToast(R.string.no_file)
                return@launch
            }

            val documentFile = DocumentFile.fromSingleUri(context, uri)
            if (documentFile == null || !documentFile.isFile || !documentFile.canRead()) {
                errorToastManager.showErrorToast(R.string.no_file)
                return@launch
            }

            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                errorToastManager.showErrorToast(R.string.no_file)
                return@launch
            }

            val encDirUri = propertyManager.getEncDirUri()
            if (encDirUri == null) {
                errorToastManager.showErrorToast(R.string.no_enc_dir)
                return@launch
            }

            val encDirFile = DocumentFile.fromTreeUri(context, Uri.parse(encDirUri))
            if (encDirFile == null || !encDirFile.isDirectory
                || !encDirFile.canRead() || !encDirFile.canWrite()
            ) {
                errorToastManager.showErrorToast(R.string.no_enc_dir)
                return@launch
            }

            val fileName = SimpleDateFormat(
                AppProperties.ENC_FILE_NAME_FORMAT,
                Locale.getDefault()
            ).format(Date())

            val newFile = encDirFile.createFile(
                "application/octet-stream",
                "$fileName.${AppProperties.ENC_FILE_EXTENSION}"
            )
            if (newFile == null || !newFile.isFile || !newFile.canWrite()) {
                errorToastManager.showErrorToast(R.string.failed_to_create_file)
                return@launch
            }

            val outputStream = context.contentResolver.openOutputStream(newFile.uri)
            if (outputStream == null) {
                errorToastManager.showErrorToast(R.string.failed_to_create_file)
                return@launch
            }

            val fileExtension = documentFile.name
                ?.substringAfterLast('.', "")
                ?: ""

            cryptoProvider.encryptEncStream(inputStream, outputStream, fileExtension)

            val newText = currentEntryText.text + fileName
            currentEntryText = currentEntryText.copy(
                text = newText,
                selection = TextRange(newText.length)
            )
        }
    }

    fun onSelectLogButtonClicked() {
        _showSelectLogDialog.value = true
    }

    fun onNewLogFileSelected(uri: Uri?) {
        if (uri == null) {
            errorToastManager.showErrorToast(R.string.no_file)
            return
        }
        logFileManager.setCurrentLogFromUri(uri)
        _showSelectLogDialog.value = false
    }

    fun hideSelectLogDialog() {
        _showSelectLogDialog.value = false
    }

    fun onKnownLogFileSelected(selected: LogFileData) {
        logFileManager.setCurrentLog(selected)
        _showSelectLogDialog.value = false
    }
}