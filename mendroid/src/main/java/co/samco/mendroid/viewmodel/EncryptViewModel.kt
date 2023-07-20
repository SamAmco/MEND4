package co.samco.mendroid.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
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
    private val errorToastManager: ErrorToastManager
) : AndroidViewModel(application) {

    private val context get() = this.getApplication<Application>().applicationContext

    var currentLogName by mutableStateOf(
        TextFieldValue(
            propertyManager.getCurrentLogName(),
            TextRange(propertyManager.getCurrentLogName().length)
        )
    )

    var currentEntryText by mutableStateOf(TextFieldValue(""))

    private val logNameFlow = snapshotFlow { currentLogName }
        .map { it.text }
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    private val logDir = propertyManager.logDirUri
        .filterNotNull()
        .map { getLogDir() }

    private fun getLogDir(): DocumentFile? {
        val uri = propertyManager.getLogDirUri() ?: return null
        val documentFile = DocumentFile.fromTreeUri(context, Uri.parse(uri))
        return if (documentFile == null || !documentFile.isDirectory
            || !documentFile.canRead() || !documentFile.canWrite()
        ) null else documentFile
    }

    val logNameValid = combine(
        logNameFlow,
        logDir.onStart { emit(null) }
    ) { currentLogName, logDirUri ->
        validLogName(currentLogName) && logDirUri != null
    }

    private fun validLogName(logName: String): Boolean {
        return listOf(
            '/',
            '\n',
            '\r',
            '\t',
            '`',
            '?',
            '*',
            '\\',
            '<',
            '>',
            '|',
            '\"',
            ':',
            '.',
            ','
        ).none { logName.contains(it) }
    }

    fun encryptText() {
        val logDir = getLogDir()
        if (logDir == null) {
            errorToastManager.showErrorToast(R.string.no_log_dir)
            return
        }

        val logName = currentLogName.text
        if (!validLogName(logName)) {
            errorToastManager.showErrorToast(R.string.invalid_log_name)
            return
        }
        val logNameWithExtension = "$logName.${AppProperties.LOG_FILE_EXTENSION}"

        val logFile = logDir.findFile(logNameWithExtension)
            ?: logDir.createFile("application/octet-stream", logNameWithExtension)

        if (logFile == null || !logFile.isFile || !logFile.canWrite()) {
            errorToastManager.showErrorToast(R.string.failed_to_create_file)
            return
        }

        val outputStream = context.contentResolver.openOutputStream(logFile.uri, "wa")
        if (outputStream == null) {
            errorToastManager.showErrorToast(R.string.failed_to_create_file)
            return
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

    fun encryptFile(uri: Uri?) {
        if (uri == null) {
            errorToastManager.showErrorToast(R.string.no_file)
            return
        }

        val documentFile = DocumentFile.fromSingleUri(context, uri)
        if (documentFile == null || !documentFile.isFile || !documentFile.canRead()) {
            errorToastManager.showErrorToast(R.string.no_file)
            return
        }

        val inputStream = context.contentResolver.openInputStream(uri)
        if (inputStream == null) {
            errorToastManager.showErrorToast(R.string.no_file)
            return
        }

        val encDirUri = propertyManager.getEncDirUri()
        if (encDirUri == null) {
            errorToastManager.showErrorToast(R.string.no_enc_dir)
            return
        }

        val encDirFile = DocumentFile.fromTreeUri(context, Uri.parse(encDirUri))
        if (encDirFile == null || !encDirFile.isDirectory
            || !encDirFile.canRead() || !encDirFile.canWrite()
        ) {
            errorToastManager.showErrorToast(R.string.no_enc_dir)
            return
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
            return
        }

        val outputStream = context.contentResolver.openOutputStream(newFile.uri)
        if (outputStream == null) {
            errorToastManager.showErrorToast(R.string.failed_to_create_file)
            return
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

    init {
        viewModelScope.launch {
            logNameFlow.collect {
                propertyManager.setCurrentLogName(it)
            }
        }
    }
}