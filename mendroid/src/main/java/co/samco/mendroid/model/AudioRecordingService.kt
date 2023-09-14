package co.samco.mendroid.model

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
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
        observeRecordingRequests()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning = false
    }

    private fun observeRecordingRequests() {
        launch {
            onRequestStartRecordingToFile
                .filter { isRecording.value.not() }
                .collect { file ->

                    val mediaRecorder =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            MediaRecorder(applicationContext)
                        else MediaRecorder()

                    mediaRecorder.apply {
                        setAudioSource(MediaRecorder.AudioSource.MIC)
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