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
import co.samco.mendroid.BuildConfig
import co.samco.mendroid.R
import co.samco.mendroid.remote.RemoteReadCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.PrintStream
import java.security.PrivateKey
import java.time.LocalDateTime
import java.util.Collections
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
    private val propertyManager: PropertyManager,
    @ApplicationContext private val context: Context,
    private val remoteCache: RemoteReadCache
) : PrivateKeyManager, CoroutineScope {

    companion object {
        const val DECRYPT_DIR = "decrypted"
    }

    override val coroutineContext: CoroutineContext = Job()

    private val sessionLock = Any()
    private var sessionGeneration = 0L
    private var privateKey: PrivateKey? = null
    private val mutableUnlocked = MutableStateFlow(false)
    override val unlocked = mutableUnlocked.asStateFlow()
    private val grantedUriList = Collections.synchronizedList(mutableListOf<Uri>())

    //TODO implement this properly
    override val decryptingFile = MutableStateFlow(false)

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
    }

    override fun forceCleanFiles() {
        remoteCache.clear()
        grantedUriList.forEach {
            context.revokeUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        grantedUriList.clear()
        File(context.filesDir, DECRYPT_DIR).deleteRecursively()
    }

    override val decryptingLog = MutableStateFlow(false)

    private val mutableLogLines = MutableStateFlow<List<LogLine>>(emptyList())
    override val decryptedLogLines = mutableLogLines.asStateFlow()

    private val dateTimeRegex = Regex("""(\d{2})/(\d{2})/(\d{4}) (\d{2}):(\d{2}):(\d{2})""")
    private val logSplitter =
        Regex("""(?=${dateTimeRegex.pattern}//MEND.+//.+${AppProperties.DIVIDER_SLASHES})""")

    override suspend fun decryptLog(inputStream: InputStream) {
        if (decryptingLog.value) return

        val session = synchronized(sessionLock) { privateKey?.let { it to sessionGeneration } }
        if (session == null) {
            errorToastManager.showErrorToast(R.string.no_private_key)
            return
        }

        val (privKey, generation) = session
        decryptingLog.value = true
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

            synchronized(sessionLock) {
                if (generation == sessionGeneration && mutableUnlocked.value) mutableLogLines.value = logEntries
            }
        } catch (e: CancellationException) {
            throw e
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
        val generation = synchronized(sessionLock) { sessionGeneration }
        val result = try {
            cryptoProvider.unlock(pass)
        } catch (e: Exception) {
            errorToastManager.showErrorToast(R.string.unlock_crashed)
            return false
        }
        if (result !is UnlockResult.Success) return false
        return synchronized(sessionLock) {
            if (generation != sessionGeneration) false
            else {
                sessionGeneration++
                remoteCache.openSession()
                privateKey = result.privateKey
                mutableUnlocked.value = true
                true
            }
        }
    }

    private fun lockMend() = synchronized(sessionLock) {
        sessionGeneration++
        privateKey = null
        mutableUnlocked.value = false
        mutableLogLines.value = emptyList()
        cancelDecryptingFile()
        forceCleanFiles()
    }

    override fun onUserLockMend() { lockMend() }

    override fun onScreenOff() {
        if (propertyManager.getLockOnScreenLock()) lockMend()
    }

    private var decryptFileJob: Job? = null

    override fun decryptEncFile(fileUri: Uri) {

        val session = synchronized(sessionLock) { privateKey?.let { it to sessionGeneration } }
        if (session == null) {
            errorToastManager.showErrorToast(R.string.no_private_key)
            return
        }

        val (privKey, generation) = session
        decryptFileJob?.cancel()

        decryptFileJob = launch(Dispatchers.IO) {
            decryptingFile.emit(true)

            val tempFile = createTempDecryptFile()

            if (tempFile == null) {
                errorToastManager.showErrorToast(R.string.failed_to_decrypt_file)
                return@launch
            }

            var offered = false
            try {
                decryptToTempFile(tempFile, fileUri, privKey)?.let { extension ->
                    yield()
                    synchronized(sessionLock) {
                        if (generation == sessionGeneration && mutableUnlocked.value) {
                            offerFileView(tempFile, extension)
                            offered = true
                        }
                    }
                }
            } finally {
                if (!offered) tempFile.delete()
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
        val uri = FileProvider
            .getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
            .also { grantedUriList.add(it) }

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
