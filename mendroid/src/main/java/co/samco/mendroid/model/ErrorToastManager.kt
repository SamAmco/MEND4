package co.samco.mendroid.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

interface ErrorToastManager {
    val errorToast: SharedFlow<ErrorToast>
    fun showErrorToast(messageId: Int, formatArgs: List<String> = emptyList())
}

class ErrorToastManagerImpl @Inject constructor() : CoroutineScope, ErrorToastManager {
    override val coroutineContext: CoroutineContext = Job()

    private val _errorToast = MutableSharedFlow<ErrorToast>()
    override val errorToast = _errorToast.shareIn(this, SharingStarted.Lazily, replay = 0)

    override fun showErrorToast(messageId: Int, formatArgs: List<String>) {
        launch {
            _errorToast.emit(ErrorToast(messageId, formatArgs))
        }
    }
}