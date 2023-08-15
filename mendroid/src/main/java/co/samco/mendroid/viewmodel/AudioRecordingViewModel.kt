package co.samco.mendroid.viewmodel

import android.app.Application
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.samco.mendroid.R
import co.samco.mendroid.model.EncryptHelper
import co.samco.mendroid.model.ErrorToastManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import java.io.File
import java.lang.Integer.max
import javax.inject.Inject

@HiltViewModel
class AudioRecordingViewModel @Inject constructor(
    application: Application,
    private val errorToastManager: ErrorToastManager,
    private val encryptHelper: EncryptHelper
) : AndroidViewModel(application) {

    private val context get() = this.getApplication<Application>().applicationContext

    var recording: Boolean by mutableStateOf(false)
        private set

    private val tempFile = MutableStateFlow<File?>(null)

    private val recordingFlow = snapshotFlow { recording }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val hasRecording = tempFile
        .flatMapLatest {
            recordingFlow
                //Wait until we start recording
                .dropWhile { !it }
                //Wait until we stop recording
                .filter { !it }
                .take(1)
                //If we have a file, we have a recording
                .map { tempFile.value != null }
                .onStart { emit(false) }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val lastRecordingStart = recordingFlow
        .filter { it }
        .map { System.currentTimeMillis() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, System.currentTimeMillis())

    val timeText: StateFlow<String> =
        combine(recordingFlow, hasRecording) { recording, hasRecording ->
            return@combine if (!recording && !hasRecording) flow { emit("00:00") }
            else {
                if (recording) flow {
                    while (true) {
                        emit(getTimeDiffAsString(System.currentTimeMillis() - lastRecordingStart.value))
                        delay(1000)
                    }
                }
                else flow {
                    emit(getTimeDiffAsString(System.currentTimeMillis() - lastRecordingStart.value))
                }
            }
        }
            .flatMapLatest { it }
            .stateIn(viewModelScope, SharingStarted.Eagerly, "00:00")

    var loading: Boolean by mutableStateOf(false)
        private set

    private val audioDir by lazy {
        File(context.cacheDir, "audio").apply { mkdirs() }
    }

    val showAudioRecordingDialog = tempFile
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val audioRecorder = tempFile
        .map {
            it?.let { file ->
                val mediaRecorder =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
                    else MediaRecorder()

                mediaRecorder.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB)
                    setOutputFile(file)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun showAudioRecordingDialog() {
        if (tempFile.value != null) return
        createTempFile()
    }

    private fun getTimeDiffAsString(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60

        val minuteDigits = max(2, minutes.toString().length)

        return "%0${minuteDigits}d:%02d".format(minutes, seconds % 60)
    }

    private fun createTempFile() {
        tempFile.value = File.createTempFile("file", ".mp4", audioDir).apply {
            deleteOnExit()
        }
    }

    private fun clearFiles() {
        audioDir.listFiles()?.forEach { it.delete() }
    }

    fun dismissRecordAudioDialog() {
        clearFiles()
        tempFile.value = null
        recording = false
        audioRecorder.value?.release()
    }

    fun startRecording() {
        audioRecorder.value?.apply {
            prepare()
            start()
        }
        recording = true
    }

    fun stopRecording() {
        recording = false
        audioRecorder.value?.apply {
            stop()
            release()
        }
    }

    fun retryRecording() {
        clearFiles()
        createTempFile()
    }

    fun saveRecording() {
        viewModelScope.launch {
            loading = true
            val uri = tempFile.value?.toUri()

            if (uri == null) errorToastManager.showErrorToast(R.string.failed_to_take_audio)
            else encryptHelper.encryptFileFromUri(uri)

            loading = false
            dismissRecordAudioDialog()
        }
    }
}