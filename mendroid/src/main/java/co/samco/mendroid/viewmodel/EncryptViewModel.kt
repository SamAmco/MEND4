package co.samco.mendroid.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.core.util.LogUtils
import co.samco.mendroid.BuildConfig
import co.samco.mendroid.model.ErrorToastManager
import co.samco.mendroid.R
import co.samco.mendroid.model.EncryptHelper
import co.samco.mendroid.model.LogFileData
import co.samco.mendroid.model.LogFileManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class EncryptViewModel @Inject constructor(
    application: Application,
    private val cryptoProvider: CryptoProvider,
    private val encryptHelper: EncryptHelper,
    private val errorToastManager: ErrorToastManager,
    private val logFileManager: LogFileManager
) : AndroidViewModel(application) {

    companion object {
        private const val TO_ENCRYPT_DIR = "to-encrypt"
    }

    private val _offerDeleteFile = MutableStateFlow<Uri?>(null)
    val showDeleteFileDialog: StateFlow<Boolean> = _offerDeleteFile
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

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

    var loading by mutableStateOf(false)
        private set

    private var currentUri: Uri? = null

    var showAttachmentMenu by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            encryptHelper.onFileEncrypted.collect { fileName ->
                val newText = "${currentEntryText.text}$fileName "
                currentEntryText = currentEntryText.copy(
                    text = newText,
                    selection = TextRange(newText.length)
                )
            }
        }
    }

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
        showAttachmentMenu = false
        viewModelScope.launch {
            if (uri == null) {
                errorToastManager.showErrorToast(R.string.no_file)
                return@launch
            }

            loading = true
            encryptHelper.encryptFileFromUri(uri)
            loading = false

            //Get the column flags and check for SUPPORTS_DELETE
            val flags = context.contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
                null,
                null,
                null
            )?.use {
                if (it.moveToFirst()) it.getInt(0) else 0
            } ?: 0

            //if the file supports delete, offer to delete it
            if (flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0) {
                _offerDeleteFile.value = uri
            } else {
                errorToastManager.showErrorToast(R.string.can_not_offer_delete)
            }
        }
    }

    fun onSelectLogButtonClicked() {
        logFileManager.forcePruneKnownLogs()
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

    fun dismissOfferDeleteFileDialog() {
        _offerDeleteFile.value = null
        errorToastManager.showErrorToast(R.string.file_not_deleted)
    }

    fun deleteFile() {
        val success = _offerDeleteFile.value
            ?.let { DocumentFile.fromSingleUri(context, it) }
            ?.delete() == true

        _offerDeleteFile.value = null

        errorToastManager.showErrorToast(
            if (success) R.string.file_deleted
            else R.string.failed_to_delete_file
        )
    }

    fun hideAttachmentMenu() {
        showAttachmentMenu = false
    }

    fun showAttachmentMenu() {
        showAttachmentMenu = true
    }

    private val toEncryptDir by lazy { File(context.externalCacheDir, TO_ENCRYPT_DIR) }

    private fun prepareUri(extension: String?): Uri? {
        return try {
            toEncryptDir.mkdirs()
            val tempFile = File.createTempFile("file", extension, toEncryptDir)
            tempFile.deleteOnExit()
            currentUri = FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                tempFile
            )
            currentUri
        } catch (e: IOException) {
            errorToastManager.showErrorToast(R.string.failed_to_take_photo)
            null
        }
    }

    fun preparePhotoUri(): Uri? = prepareUri(".jpg")

    fun onPhotoTaken(success: Boolean) {
        showAttachmentMenu = false
        val uri = currentUri
        if (!success || uri == null) {
            errorToastManager.showErrorToast(R.string.failed_to_take_photo)
            return
        }
        viewModelScope.launch {
            loading = true
            encryptHelper.encryptFileFromUri(uri)
            clearCacheDir()
            loading = false
        }
    }

    private fun clearCacheDir() {
        toEncryptDir.listFiles()?.forEach { it.delete() }
    }

    fun prepareVideoUri(): Uri? = prepareUri(".mp4")

    fun onVideoTaken(success: Boolean) {
        showAttachmentMenu = false
        val uri = currentUri
        if (!success || uri == null) {
            errorToastManager.showErrorToast(R.string.failed_to_take_video)
            return
        }
        viewModelScope.launch {
            loading = true
            encryptHelper.encryptFileFromUri(uri)
            clearCacheDir()
            loading = false
        }
    }
}