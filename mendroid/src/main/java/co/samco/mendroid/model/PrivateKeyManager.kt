package co.samco.mendroid.model

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import co.samco.mend4.core.AppProperties
import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.core.crypto.UnlockResult
import co.samco.mendroid.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.PrintStream
import java.security.PrivateKey
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

data class LogLine(
    val text: String,
    val dateTime: LocalDateTime?
)

/**
 * Manages the private key and its unlock state. This is the only place that should access
 * the private key so any operations that requires it should be done here as well.
 */
interface PrivateKeyManager {
    val decryptingFile: StateFlow<Boolean>
    val unlocked: StateFlow<Boolean>

    val decryptingLog: StateFlow<Boolean>
    val decryptedLogLines: StateFlow<List<LogLine>>

    suspend fun decryptLog(inputStream: InputStream)

    suspend fun unlock(pass: CharArray): Boolean

    fun onUserLockMend()
    fun onScreenOff()
    fun decryptEncFile(fileUri: Uri)
    fun cancelDecryptingFile()
    fun forceCleanFiles()
}

class PrivateKeyManagerImpl @Inject constructor(
    private val cryptoProvider: CryptoProvider,
    private val errorToastManager: ErrorToastManager,
    @ApplicationContext private val context: Context
) : PrivateKeyManager, CoroutineScope {

    companion object {
        const val DECRYPT_DIR = "decrypted"
    }

    override val coroutineContext: CoroutineContext = Job()

    private val screenOffEvents = MutableSharedFlow<Unit>()
    private val userLockEvents = MutableSharedFlow<Unit>()

    private val onSuccessfulUnlock = MutableSharedFlow<UnlockResult.Success>()


    private val lockEvents: SharedFlow<Unit> = merge(
        screenOffEvents,
        userLockEvents
    ).shareIn(this, SharingStarted.Eagerly, replay = 0)

    private val privateKey = merge(
        onSuccessfulUnlock.map { it.privateKey },
        lockEvents.map { null }
    ).stateIn(this, SharingStarted.Eagerly, null)

    //TODO implement this properly
    override val decryptingFile = MutableStateFlow(false)

    override val unlocked: StateFlow<Boolean> = privateKey
        .map { it != null }
        .stateIn(this, SharingStarted.Lazily, false)

    init {
        launch {
            unlocked.collect {
                if (it) ContextCompat.startForegroundService(
                    context,
                    Intent(context, LockForegroundService::class.java)
                )
                else context.stopService(
                    Intent(context, LockForegroundService::class.java)
                )
            }
        }
        launch {
            unlocked.collect {
                if (!it) forceCleanFiles()
            }
        }
    }

    override fun forceCleanFiles() {
        File(context.filesDir, DECRYPT_DIR).deleteRecursively()
    }

    override val decryptingLog = MutableStateFlow(false)

    private val onLogLinesDecrypted = MutableSharedFlow<List<LogLine>>()
    override val decryptedLogLines = merge(
        onLogLinesDecrypted,
        lockEvents.map { emptyList() }
    ).stateIn(this, SharingStarted.Eagerly, emptyList())


    private val dateTimeRegex = Regex("""(\d{2})/(\d{2})/(\d{4}) (\d{2}):(\d{2}):(\d{2})""")
    private val logSplitter =
        Regex("""(?=${dateTimeRegex.pattern}//MEND.+//.+${AppProperties.DIVIDER_SLASHES})""")

    override suspend fun decryptLog(inputStream: InputStream) {
        if (decryptingLog.value) return

        decryptingLog.value = true

        val privKey = privateKey.value
        if (privKey == null) {
            errorToastManager.showErrorToast(R.string.no_private_key)
            return
        }

        try {
            val byteArrayOutputStream = ByteArrayOutputStream()
            val outputStream = PrintStream(byteArrayOutputStream)
            cryptoProvider.decryptLogStream(
                privateKey = privKey,
                inputStream = inputStream,
                outputStream = outputStream
            )
            val utf8Text = String(byteArrayOutputStream.toByteArray(), Charsets.UTF_8)
            val logEntries = utf8Text
                .split(logSplitter)
                .filter { it.isNotBlank() }
                .map { getLogLine(it) }

            onLogLinesDecrypted.emit(logEntries)
        } catch (t: Throwable) {
            t.printStackTrace()
            errorToastManager.showErrorToast(
                R.string.decrypt_crashed,
                t.message?.let { listOf(it) } ?: listOf(""))
            return
        } finally {
            decryptingLog.value = false
        }
    }

    private fun getLogLine(string: String): LogLine {
        val dateTimeMatch = dateTimeRegex.find(string)

        val dateTime = if (dateTimeMatch != null) {
            val (day, month, year, hour, minute, second) = dateTimeMatch.destructured
            LocalDateTime.of(
                /* year = */ year.toInt(),
                /* month = */ month.toInt(),
                /* dayOfMonth = */ day.toInt(),
                /* hour = */ hour.toInt(),
                /* minute = */ minute.toInt(),
                /* second = */ second.toInt()
            )
        } else null

        val text = string
            .substringAfter(AppProperties.DIVIDER_SLASHES)
            .trim()

        return LogLine(
            text = text,
            dateTime = dateTime
        )
    }

    override suspend fun unlock(pass: CharArray): Boolean {
        val unlockResult = try {
            cryptoProvider.unlock(pass)
        } catch (e: Exception) {
            e.printStackTrace()
            UnlockResult.Failure
            errorToastManager.showErrorToast(R.string.unlock_crashed)
            return false
        }

        return if (unlockResult is UnlockResult.Success) {
            onSuccessfulUnlock.emit(unlockResult)
            true
        } else false
    }

    override fun onUserLockMend() {
        launch { userLockEvents.emit(Unit) }
    }

    override fun onScreenOff() {
        launch { screenOffEvents.emit(Unit) }
    }

    private var decryptFileJob: Job? = null

    override fun decryptEncFile(fileUri: Uri) {

        val privKey = privateKey.value
        if (privKey == null) {
            errorToastManager.showErrorToast(R.string.no_private_key)
            return
        }

        decryptFileJob?.cancel()

        decryptFileJob = launch(Dispatchers.IO) {
            decryptingFile.emit(true)

            val tempFile = createTempDecryptFile()

            if (tempFile == null) {
                errorToastManager.showErrorToast(R.string.failed_to_decrypt_file)
                return@launch
            }

            decryptToTempFile(tempFile, fileUri, privKey)?.let {
                //yield in case the coroutine is cancelled before the file is opened
                //yield()
                offerFileView(tempFile, it)
            }
        }

        launch {
            try {
                decryptFileJob?.join()
            } catch (t: Throwable) {
                t.printStackTrace()
                errorToastManager.showErrorToast(R.string.failed_to_decrypt_file)
            }
            decryptingFile.emit(false)
        }
    }

    private suspend fun decryptToTempFile(
        tempFile: File,
        fileUri: Uri,
        privKey: PrivateKey
    ): String? {
        try {
            context.contentResolver.openOutputStream(tempFile.toUri()).use { outputStream ->
                if (outputStream == null) {
                    errorToastManager.showErrorToast(R.string.failed_to_decrypt_file)
                    return null
                }
                context.contentResolver.openInputStream(fileUri).use { inputStream ->
                    if (inputStream == null) {
                        errorToastManager.showErrorToast(R.string.failed_to_decrypt_file)
                        return null
                    }

                    return cryptoProvider.decryptEncStream(
                        privKey,
                        inputStream,
                        outputStream
                    )
                }
            }
        } catch (c: CancellationException) {
            errorToastManager.showErrorToast(R.string.decrypt_cancelled)
            return null
        } catch (t: Throwable) {
            t.printStackTrace()
            errorToastManager.showErrorToast(
                R.string.decrypt_crashed,
                listOf(t.message ?: "")
            )
            return null
        }
    }

    private fun createTempDecryptFile(): File? {
        return try {
            val uuid = UUID.randomUUID().toString()
            val decryptDir = File(context.filesDir, DECRYPT_DIR)
            decryptDir.mkdirs()
            val tempFile = File(decryptDir, uuid)
            if (tempFile.exists()) tempFile.delete()
            if (!tempFile.createNewFile()) return null
            tempFile
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    private fun offerFileView(file: File, extension: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "co.samco.mendroid.fileprovider",
            file
        )

        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)

        val shareContentIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setDataAndType(uri, mimeType)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }

        if (shareContentIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(shareContentIntent)
            return
        } else errorToastManager.showErrorToast(R.string.no_app_to_open_file, listOf(extension))
    }

    override fun cancelDecryptingFile() {
        decryptFileJob?.cancel()
        decryptingFile.value = false
    }
}