package co.samco.mendroid.model

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import co.samco.mendroid.MainActivity
import co.samco.mendroid.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.CoroutineContext

interface AudioRecorder {
    fun startRecording(location: File)
    suspend fun stopRecording(): File?
    fun cancelRecording()
    fun isRecording(): Flow<Boolean>
    fun lastRecordingStartTime(): Flow<Long>
}

class AudioRecorderService : Service(), CoroutineScope {

    override val coroutineContext: CoroutineContext = Job() + Dispatchers.IO

    private val binder = AudioRecorderBinder()

    private val onRequestStartRecordingToFile = MutableSharedFlow<File>(extraBufferCapacity = 1)
    private val onRequestStopRecording = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val onRecordingStarted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val onRecordToFileComplete = MutableSharedFlow<File>(extraBufferCapacity = 1)

    private val isRecording = merge(
        onRecordingStarted.map { true },
        onRecordToFileComplete.map { false }
    ).stateIn(this, SharingStarted.Eagerly, false)

    private val lastRecordingStartTime = isRecording
        .filter { it }
        .map { System.currentTimeMillis() }
        .stateIn(this, SharingStarted.Eagerly, System.currentTimeMillis())

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    private fun createChannel() {
        val channelName = getString(R.string.foreground_service_audio_recorder_name)

        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            channelName,
            NotificationManager.IMPORTANCE_HIGH
        )

        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(serviceChannel)
    }

    private fun createNotification(): Notification {
        // Create a foreground notification to keep the service running
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent
            .getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.audio_recorder))
            .setContentText(getString(R.string.recording_in_progress))
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        _isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(FOREGROUND_SERVICE_ID, createNotification())
        selectRecordingDevice()
        observeRecordingRequests()
        return START_NOT_STICKY
    }

    private fun selectRecordingDevice() {
        val audioManager = getSystemService(AudioManager::class.java)

        val preferredDevice = audioManager.availableCommunicationDevices
            .minByOrNull {
                when (it.type) {
                    AudioDeviceInfo.TYPE_USB_HEADSET -> 0
                    AudioDeviceInfo.TYPE_USB_DEVICE -> 1
                    AudioDeviceInfo.TYPE_USB_ACCESSORY -> 2
                    AudioDeviceInfo.TYPE_WIRED_HEADSET -> 3

                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> 4
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> 5
                    AudioDeviceInfo.TYPE_BLE_HEADSET -> 6

                    AudioDeviceInfo.TYPE_BUILTIN_MIC -> 7
                    AudioDeviceInfo.TYPE_TELEPHONY -> 8
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> 9
                    else -> 10
                }
            }

        preferredDevice?.let { audioManager.setCommunicationDevice(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        getSystemService(AudioManager::class.java).clearCommunicationDevice()
        _isRunning = false
    }

    private fun observeRecordingRequests() {
        launch {
            onRequestStartRecordingToFile
                .filter { isRecording.value.not() }
                .collect { file ->

                    val mediaRecorder = MediaRecorder(applicationContext)

                    mediaRecorder.apply {
                        setAudioSource(MediaRecorder.AudioSource.DEFAULT)
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB)
                        setOutputFile(file)
                        prepare()
                        start()
                    }

                    onRecordingStarted.emit(Unit)

                    onRequestStopRecording.take(1).collect {
                        mediaRecorder.stop()
                        mediaRecorder.release()
                        onRecordToFileComplete.emit(file)
                    }
                }
        }
    }

    inner class AudioRecorderBinder : Binder(), AudioRecorder {
        override fun startRecording(file: File) {
            launch { onRequestStartRecordingToFile.emit(file) }
        }

        override suspend fun stopRecording(): File? {
            return if (isRecording.value) {
                launch { onRequestStopRecording.emit(Unit) }
                onRecordToFileComplete.first()
            } else null
        }

        override fun isRecording(): Flow<Boolean> {
            return isRecording
        }

        override fun lastRecordingStartTime(): Flow<Long> {
            return lastRecordingStartTime
        }

        override fun cancelRecording() {
            launch { onRequestStopRecording.emit(Unit) }
        }
    }

    companion object {
        private const val FOREGROUND_SERVICE_ID = 101
        private const val CHANNEL_ID = "AudioRecorderChannel"

        private var _isRunning = false
        val isRunning: Boolean
            get() {
                return _isRunning
            }
    }
}