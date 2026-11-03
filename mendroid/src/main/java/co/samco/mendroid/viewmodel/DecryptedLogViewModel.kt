@file:OptIn(ExperimentalCoroutinesApi::class)

package co.samco.mendroid.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.samco.mend4.core.AppProperties
import co.samco.mendroid.R
import co.samco.mendroid.model.ErrorToastManager
import co.samco.mendroid.model.LogLine
import co.samco.mendroid.model.PrivateKeyManager
import co.samco.mendroid.remote.AttachmentFiles
import co.samco.mendroid.upload.UploadException
import co.samco.mendroid.upload.UploadFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class TextType {
    PLAIN, FILE_ID
}

data class TextPart(
    val text: String,
    val type: TextType
)

data class LogViewData(
    val text: List<TextPart>,
    val dateTime: String?,
    val index: Int,
    val date: LocalDate?
)

data class DecryptFileDialogData(
    val fileUri: Uri,
    val fileName: String
)

@HiltViewModel
class DecryptedLogViewModel @Inject constructor(
    private val privateKeyManager: PrivateKeyManager,
    private val attachments: AttachmentFiles,
    private val errorToastManager: ErrorToastManager,
    application: Application
) : AndroidViewModel(application) {

    companion object {
        fun LogLine.asViewData(index: Int): LogViewData {
            val matches = AppProperties.ENC_FILE_NAME_PATTERN.toRegex().findAll(text)

            val textParts = mutableListOf<TextPart>()

            var currentIndex = 0

            for (match in matches) {
                val startIndex = match.range.first
                val endIndex = match.range.last + 1

                if (startIndex > currentIndex) {
                    textParts.add(
                        TextPart(
                            text = text.substring(currentIndex until startIndex),
                            type = TextType.PLAIN
                        )
                    )
                }

                textParts.add(
                    TextPart(
                        text = text.substring(startIndex until endIndex),
                        type = TextType.FILE_ID
                    )
                )
                currentIndex = endIndex
            }

            if (currentIndex < text.length) {
                textParts.add(
                    TextPart(
                        text = text.substring(currentIndex),
                        type = TextType.PLAIN
                    )
                )
            }

            return LogViewData(
                text = textParts,
                dateTime = dateTime?.format(DateTimeFormatter.ofPattern("EEE, yyyy-MM-dd HH:mm:ss")),
                index = index,
                date = dateTime?.toLocalDate()
            )
        }
    }

    var searchText by mutableStateOf(TextFieldValue(""))

    var filterEnabled by mutableStateOf(true)

    private val _decryptFileDialogData = MutableStateFlow<DecryptFileDialogData?>(null)
    val decryptFileDialogData = _decryptFileDialogData.asStateFlow()
    private val resolvingAttachment = MutableStateFlow(false)
    val downloadingFile = resolvingAttachment.asStateFlow()
    private var lookupJob: Job? = null
    private var lookupGeneration = 0

    init {
        viewModelScope.launch {
            privateKeyManager.unlocked.collect { if (!it) onCancelDecryptFileClicked() }
        }
    }

    val logLines: StateFlow<List<LogViewData>> = combine(
        privateKeyManager.decryptedLogLines,
        snapshotFlow { filterEnabled },
        snapshotFlow { searchText }
    ) { lines, filterEnabled, search ->
        if (!filterEnabled) return@combine lines.mapIndexed { i, l -> l.asViewData(i) }

        val ignoreCase = search.text.none { it.isUpperCase() }

        if (search.text.isEmpty()) emptyList()
        else {
            lines
                .asSequence()
                .mapIndexed { index, line -> Pair(index, line) }
                .filter { it.second.text.contains(search.text, ignoreCase = ignoreCase) }
                .map { it.second.asViewData(it.first) }
                .toList()
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val decryptingLog = privateKeyManager.decryptingLog
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val decryptingFile = combine(privateKeyManager.decryptingFile, resolvingAttachment) { decrypting, resolving -> decrypting || resolving }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _onScrollToIndex = MutableSharedFlow<Int>()
    val scrollToIndex: Flow<Int> = _onScrollToIndex
        .flatMapLatest { index ->
            logLines.drop(1).map {
                delay(100)
                index
            }
        }
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 0)

    var firstVisibleItemIndex = 0
        private set
    var firstVisibleItemScrollOffset = 0
        private set
    var searchComplete = false
        private set

    fun onSearchComplete() {
        searchComplete = true
    }

    fun storeListState(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) {
        this.firstVisibleItemIndex = firstVisibleItemIndex
        this.firstVisibleItemScrollOffset = firstVisibleItemScrollOffset
    }

    fun onLogLineClicked(logViewData: LogViewData) {
        filterEnabled = false
        viewModelScope.launch { _onScrollToIndex.emit(logViewData.index) }
    }

    fun onDecryptFileClicked(dialogData: DecryptFileDialogData) {
        if (!privateKeyManager.unlocked.value) return
        privateKeyManager.decryptEncFile(dialogData.fileUri)
        _decryptFileDialogData.value = null
    }

    fun onCancelDecryptFileClicked() {
        lookupGeneration++
        lookupJob?.cancel()
        resolvingAttachment.value = false
        privateKeyManager.cancelDecryptingFile()
        _decryptFileDialogData.value = null
    }

    fun onFileIdClicked(fileId: String) {
        if (!privateKeyManager.unlocked.value) return
        lookupJob?.cancel()
        val request = ++lookupGeneration
        val fileName = "$fileId.${AppProperties.ENC_FILE_EXTENSION}"
        resolvingAttachment.value = true
        lookupJob = viewModelScope.launch {
            try {
                val uri = attachments.find(fileName)
                if (uri == null) errorToastManager.showErrorToast(R.string.could_not_find_enc_file, listOf(fileName))
                else if (request == lookupGeneration && privateKeyManager.unlocked.value) _decryptFileDialogData.value = DecryptFileDialogData(uri, fileName)
            } catch (e: CancellationException) { throw e }
            catch (e: UploadException) {
                errorToastManager.showErrorToast(if (e.reason == UploadFailure.AUTHENTICATION || e.reason == UploadFailure.ACCOUNT_UNAVAILABLE)
                    R.string.remote_read_auth else R.string.remote_read_error)
            } catch (_: Exception) { errorToastManager.showErrorToast(R.string.remote_read_error) }
            finally { if (request == lookupGeneration) resolvingAttachment.value = false }
        }
    }
}
