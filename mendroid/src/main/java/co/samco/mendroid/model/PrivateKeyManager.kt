@file:OptIn(ExperimentalCoroutinesApi::class)

package co.samco.mendroid.model

import co.samco.mend4.core.AppProperties
import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.core.crypto.UnlockResult
import co.samco.mendroid.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.PrintStream
import java.time.LocalDateTime
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
    val unlocked: StateFlow<Boolean>

    val decryptingLog: StateFlow<Boolean>
    val decryptedLogLines: StateFlow<List<LogLine>>

    fun decryptLog(inputStream: InputStream)

    suspend fun unlock(pass: CharArray): Boolean

    fun onActivityStop()
    fun onActivityStart()
    fun onGoToEncrypt()
}

class PrivateKeyManagerImpl @Inject constructor(
    private val cryptoProvider: CryptoProvider,
    private val errorToastManager: ErrorToastManager
) : PrivateKeyManager, CoroutineScope {

    companion object {
        //How long after navigating away do we lock mend if it's unlocked
        const val LOCK_TIMEOUT = 1 * 1000L
    }

    override val coroutineContext: CoroutineContext = Job()

    private val lockOnEncrypt = MutableSharedFlow<Unit>()
    private val onSuccessfulUnlock = MutableSharedFlow<UnlockResult.Success>()
    private val activityStartEvents = MutableSharedFlow<Unit>()
    private val activityStopEvents = MutableSharedFlow<Unit>()

    private val lockOnActivityStop = activityStopEvents
        .flatMapLatest {
            merge(
                flow {
                    delay(LOCK_TIMEOUT)
                    emit(true)
                },
                activityStartEvents.map { false }
            ).take(1)
        }
        .filter { it }
        .map { }

    private val lockEvents: SharedFlow<Unit> = merge(
        lockOnEncrypt,
        lockOnActivityStop
    ).shareIn(this, SharingStarted.Eagerly, replay = 0)

    private val privateKey = merge(
        onSuccessfulUnlock.map { it.privateKey },
        lockEvents.map { null }
    ).stateIn(this, SharingStarted.Eagerly, null)

    override val unlocked: StateFlow<Boolean> = privateKey
        .map { it != null }
        .stateIn(this, SharingStarted.Lazily, false)

    override val decryptingLog = MutableStateFlow(false)

    private val onLogLinesDecrypted = MutableSharedFlow<List<LogLine>>()
    override val decryptedLogLines = merge(
        onLogLinesDecrypted,
        lockEvents.map { emptyList() }
    ).stateIn(this, SharingStarted.Eagerly, emptyList())


    private val dateTimeRegex = Regex("""(\d{2})/(\d{2})/(\d{4}) (\d{2}):(\d{2}):(\d{2})""")
    private val logSplitter =
        Regex("""(?=${dateTimeRegex.pattern}//MEND.+//.+${AppProperties.DIVIDER_SLASHES})""")

    override fun decryptLog(inputStream: InputStream) {
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

            launch { onLogLinesDecrypted.emit(logEntries) }
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

    override fun onActivityStop() {
        launch { activityStopEvents.emit(Unit) }
    }

    override fun onActivityStart() {
        launch { activityStartEvents.emit(Unit) }
    }

    override fun onGoToEncrypt() {
        launch { lockOnEncrypt.emit(Unit) }
    }
}