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
import co.samco.mendroid.R
import co.samco.mendroid.model.AudioRecorder
import co.samco.mendroid.model.AudioRecorderService
import co.samco.mendroid.model.EncryptHelper
import co.samco.mendroid.model.ErrorToastManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.lang.Integer.max
import javax.inject.Inject

interface AudioRecordingViewModel {
    val isRecording: StateFlow<Boolean>
    val loading: Boolean

    val timeText: StateFlow<String>
    val hasRecording: StateFlow<Boolean>
    val showAudioRecordingDialog: StateFlow<Boolean>

    fun showAudioRecordingDialog()
    fun dismissRecordAudioDialog()
    fun startRecording()
    fun stopRecording()
    fun retryRecording()
    fun saveRecording()
}

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

    override val isRecording: StateFlow<Boolean> = audioRecorderFlow
        .filterNotNull()
        .flatMapLatest { it.isRecording() }
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
        if (audioRecorderFlow.value != null) return
        Intent(context, AudioRecorderService::class.java).apply {
            context.bindService(this, serviceConnection, Context.BIND_AUTO_CREATE)
            context.startService(this)
        }
        viewModelScope.launch {
            audioRecorderFlow
                .filterNotNull()
                .first()
                .startRecording(createTempFile())
        }
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