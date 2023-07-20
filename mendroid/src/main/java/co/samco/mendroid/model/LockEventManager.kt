@file:OptIn(ExperimentalCoroutinesApi::class)

package co.samco.mendroid.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

interface LockEventManager {
    val lockEvents: SharedFlow<Unit>

    fun onActivityStop()
    fun onActivityStart()
    fun onGoToEncrypt()
}

class LockEventManagerImpl @Inject constructor() : LockEventManager, CoroutineScope {

    companion object {
        //How long after navigating away do we lock mend if it's unlocked
        const val LOCK_TIMEOUT = 1 * 1000L
    }

    override val coroutineContext: CoroutineContext = Job()

    private val lockOnEncrypt = MutableSharedFlow<Unit>()

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

    override val lockEvents: SharedFlow<Unit> = merge(
        lockOnEncrypt,
        lockOnActivityStop
    ).shareIn(this, SharingStarted.Eagerly, replay = 0)

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