package co.samco.mendroid.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

@Singleton
class FileEventManager @Inject constructor() : CoroutineScope {

    override val coroutineContext: CoroutineContext = Job()

    private val _onNewLogCreated = MutableSharedFlow<Unit>()

    val onNewLogCreated = _onNewLogCreated
        .shareIn(this, SharingStarted.Eagerly, replay = 0)

    fun onNewLogCreated() = launch {
        _onNewLogCreated.emit(Unit)
    }
}