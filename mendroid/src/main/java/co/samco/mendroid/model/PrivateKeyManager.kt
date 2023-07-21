@file:OptIn(ExperimentalCoroutinesApi::class)

package co.samco.mendroid.model

import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.core.crypto.UnlockResult
import co.samco.mendroid.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

/**
 * Manages the private key and its unlock state. This is the only place that should access
 * the private key so any operations that requires it should be done here as well.
 */
interface PrivateKeyManager {
    val unlocked: StateFlow<Boolean>

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