package co.samco.mendroid.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import co.samco.mendroid.R
import co.samco.mendroid.model.AudioRecorder
import co.samco.mendroid.model.AudioRecorderService
import co.samco.mendroid.model.EncryptHelper
import co.samco.mendroid.model.ErrorToastManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.Integer.max
import javax.inject.Inject

enum class TestRecordingState {
    WAITING,
    RECORDING,
    PLAYING_BACK
}

interface AudioRecordingViewModel {
    val isRecording: StateFlow<Boolean>
    val loading: Boolean

    val timeText: StateFlow<String>
    val hasRecording: StateFlow<Boolean>
    val showAudioRecordingDialog: StateFlow<Boolean>

    val testRecordingState: StateFlow<TestRecordingState>

    fun startTestRecording()
    fun stopTestRecording()
    fun finishTestRecording()

    fun showAudioRecordingDialog()
    fun dismissRecordAudioDialog()
    fun startRecording()
    fun stopRecording()
    fun retryRecording()
    fun saveRecording()
}

/**
 * There's a lot of ugliness here. Probably this is a prime candidate for refactoring. There are two
 * different patterns being used in the same class for recording and they share an object that they
 * can potentially both mutate. They are using something a bit like flags to try and avoid conflicting
 * but i'm certain there are hidden race conditions in here if you look hard enough. Probably it should
 * be migrated to a single pattern and the recording logic for both test and actual recordings should
 * be unified to be certain only one can be active at a time. I think the test recording pattern is
 * a bit cleaner because it is declarative and doesn't depend on concurrently mutating local state.
 * With that said the while loop pattern will quickly become un-manageable at scale, so some thinking
 * needs to be done about how best to refactor that.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AudioRecordingViewModelImpl @Inject constructor(
    application: Application,
    private val errorToastManager: ErrorToastManager,
    private val encryptHelper: EncryptHelper
) : AndroidViewModel(application), AudioRecordingViewModel {

    private val context get() = this.getApplication<Application>().applicationContext

    private val audioRecorderFlow = MutableStateFlow<AudioRecorder?>(null)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioRecorderService.AudioRecorderBinder
            audioRecorderFlow.tryEmit(binder)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioRecorderFlow.tryEmit(null)
        }
    }

    override val showAudioRecordingDialog = MutableStateFlow(false)

    init {
        //try to bind to the service only if it is already running
        if (AudioRecorderService.isRunning) {
            Intent(context, AudioRecorderService::class.java).apply {
                context.bindService(this, serviceConnection, Context.BIND_AUTO_CREATE)
            }
            showAudioRecordingDialog.value = true
        }
    }

    private val onStartTestRecording = MutableSharedFlow<Unit>()
    private val onStopTestRecording = MutableSharedFlow<Unit>()
    private val onFinishTestRecording = MutableSharedFlow<Unit>()

    @OptIn(FlowPreview::class)
    override val testRecordingState: StateFlow<TestRecordingState> = channelFlow {
        while (isActive) {
            send(TestRecordingState.WAITING)

            //Record
            onStartTestRecording.first()
            val startFile = createTempFile()
            if (!tryRecording(startFile)) {
                tryDeleteFile(startFile)
                continue
            }
            send(TestRecordingState.RECORDING)

            //Stop
            onStopTestRecording.debounce(500).first()
            val file = audioRecorderFlow.value?.stopRecording()
            if (file == null) {
                //Don't love sending events from here, being a bit lazy
                errorToastManager.showErrorToast(R.string.failed_to_take_audio)
                tryDeleteFile(startFile)
                continue
            }
            stopAndUnbindAudioRecorderService()
            val exoPlayer = ExoPlayer.Builder(context).build()
            playAudioFile(exoPlayer, file)
            send(TestRecordingState.PLAYING_BACK)

            //Reset
            onFinishTestRecording.first()
            stopPlayback(exoPlayer)
            tryDeleteFile(file)
            send(TestRecordingState.WAITING)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TestRecordingState.WAITING)

    private suspend fun stopPlayback(player: ExoPlayer) {
        withContext(Dispatchers.Main) {
            if (player.availableCommands.contains(ExoPlayer.COMMAND_STOP)) player.stop()
            if (player.availableCommands.contains(ExoPlayer.COMMAND_GET_TRACKS)) player.release()
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private suspend fun playAudioFile(exoPlayer: ExoPlayer, file: File) {
        val dataFactory = DefaultDataSource.Factory(context)
        val mediaSource = ProgressiveMediaSource.Factory(dataFactory)
            .createMediaSource(MediaItem.fromUri(file.toUri()))

        withContext(Dispatchers.Main) {
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.repeatMode = ExoPlayer.REPEAT_MODE_ALL
            if (exoPlayer.availableCommands.contains(ExoPlayer.COMMAND_PREPARE)) exoPlayer.prepare()
            if (exoPlayer.availableCommands.contains(ExoPlayer.COMMAND_PLAY_PAUSE)) exoPlayer.play()
        }
    }

    private suspend fun tryDeleteFile(file: File) {
        withContext(Dispatchers.IO) {
            try {
                if (file.exists()) file.delete()
            } catch (e: Exception) {
                errorToastManager.showErrorToast(R.string.failed_to_delete_audio)
                e.printStackTrace()
            }
        }
    }

    override fun startTestRecording() {
        viewModelScope.launch { onStartTestRecording.emit(Unit) }
    }

    override fun stopTestRecording() {
        viewModelScope.launch { onStopTestRecording.emit(Unit) }
    }

    override fun finishTestRecording() {
        viewModelScope.launch { onFinishTestRecording.emit(Unit) }
    }


    override val isRecording: StateFlow<Boolean> = audioRecorderFlow
        .filterNotNull()
        .flatMapLatest { it.isRecording() }
        .map { it && testRecordingState.value != TestRecordingState.RECORDING }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val currentRecording = MutableStateFlow<File?>(null)

    override val hasRecording = currentRecording
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val lastRecordingStart = audioRecorderFlow
        .filterNotNull()
        .flatMapLatest { it.lastRecordingStartTime() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, System.currentTimeMillis())

    override val timeText: StateFlow<String> =
        combine(isRecording, hasRecording) { recording, hasRecording ->
            return@combine if (!recording && !hasRecording) flow { emit("00:00") }
            else {
                lastRecordingStart.flatMapLatest { start ->
                    if (recording) flow {
                        while (true) {
                            emit(getTimeDiffAsString(System.currentTimeMillis() - start))
                            delay(1000)
                        }
                    }
                    else flow { emit(getTimeDiffAsString(System.currentTimeMillis() - start)) }
                }
            }
        }
            .flatMapLatest { it }
            .stateIn(viewModelScope, SharingStarted.Eagerly, "00:00")

    override var loading: Boolean by mutableStateOf(false)
        private set

    private val audioDir by lazy {
        File(context.cacheDir, "audio").apply { mkdirs() }
    }

    override fun showAudioRecordingDialog() {
        showAudioRecordingDialog.value = true
    }

    override fun dismissRecordAudioDialog() {
        audioRecorderFlow.value?.cancelRecording()
        stopAndUnbindAudioRecorderService()
        currentRecording.value = null
        clearFiles()
        showAudioRecordingDialog.value = false
    }

    private fun getTimeDiffAsString(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60

        val minuteDigits = max(2, minutes.toString().length)

        return "%0${minuteDigits}d:%02d".format(minutes, seconds % 60)
    }

    private fun createTempFile(): File = File
        .createTempFile("file", ".mp4", audioDir)
        .apply { deleteOnExit() }

    private fun clearFiles() {
        audioDir.listFiles()?.forEach { it.delete() }
    }

    override fun startRecording() {
        viewModelScope.launch { tryRecording(createTempFile()) }
    }

    private suspend fun tryRecording(file: File): Boolean {
        if (audioRecorderFlow.value != null) return false

        Intent(context, AudioRecorderService::class.java).apply {
            context.bindService(this, serviceConnection, Context.BIND_AUTO_CREATE)
            context.startService(this)
        }

        val audioRecorder = audioRecorderFlow.filterNotNull().first()
        if (audioRecorder.isRecording().first()) return false
        audioRecorder.startRecording(file)
        return true
    }

    override fun stopRecording() {
        viewModelScope.launch {
            audioRecorderFlow.value?.stopRecording()
                ?.let { currentRecording.value = it }
                ?: errorToastManager.showErrorToast(R.string.failed_to_take_audio)
            stopAndUnbindAudioRecorderService()
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (audioRecorderFlow.value != null) {
            context.unbindService(serviceConnection)
        }
    }

    private fun stopAndUnbindAudioRecorderService() {
        if (audioRecorderFlow.value != null) {
            context.unbindService(serviceConnection)
            audioRecorderFlow.value = null
        }
        context.stopService(Intent(context, AudioRecorderService::class.java))
    }

    override fun retryRecording() {
        clearFiles()
        currentRecording.value = null
    }

    override fun saveRecording() {
        viewModelScope.launch {
            loading = true
            val uri = currentRecording.value?.toUri()
            currentRecording.value = null

            if (uri == null) errorToastManager.showErrorToast(R.string.failed_to_take_audio)
            else encryptHelper.encryptFileFromUri(uri)

            loading = false
            dismissRecordAudioDialog()
        }
    }
}