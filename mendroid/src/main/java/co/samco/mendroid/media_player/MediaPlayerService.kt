package co.samco.mendroid.media_player

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import co.samco.mendroid.R
import co.samco.mendroid.model.PrivateKeyManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

interface MediaPlayerServiceInterface {
    fun getPlayerInstance(): ExoPlayer

    fun setMediaUri(uri: Uri)
    fun hasMediaUri(): Boolean
}

@AndroidEntryPoint
class MediaPlayerService : Service(), CoroutineScope {

    companion object {
        private const val CHANNEL_ID = "AudioPlayerServiceChannel"
        private const val NOTIFICATION_ID = 456
        private const val ACTION_STOP_SERVICE = "STOP_SERVICE"
    }

    override val coroutineContext: CoroutineContext = Job()

    @Inject
    lateinit var privateKeyManager: PrivateKeyManager

    private val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(this).build()
    }
    private var currentUri: Uri? = null

    inner class MediaPlayerBinder : Binder(), MediaPlayerServiceInterface {
        override fun getPlayerInstance() = exoPlayer

        @OptIn(UnstableApi::class)
        override fun setMediaUri(uri: Uri) {
            if (currentUri == uri) return
            currentUri = uri
            val dataFactory = DefaultDataSource.Factory(this@MediaPlayerService)
            val mediaSource = ProgressiveMediaSource.Factory(dataFactory)
                .createMediaSource(MediaItem.fromUri(uri))
            exoPlayer.setMediaSource(mediaSource)
            if (exoPlayer.availableCommands.contains(ExoPlayer.COMMAND_PREPARE)) exoPlayer.prepare()
            if (exoPlayer.availableCommands.contains(ExoPlayer.COMMAND_PLAY_PAUSE)) exoPlayer.play()
        }

        override fun hasMediaUri() = currentUri != null
    }

    private val binder = MediaPlayerBinder()

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channelName = getString(R.string.foreground_service_channel_name)

        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            channelName,
            NotificationManager.IMPORTANCE_HIGH
        )

        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(serviceChannel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            cleanUp()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        observeUnlockState()
        return START_STICKY
    }

    @SuppressLint("LaunchActivityFromNotification")
    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MediaPlayerActivity::class.java)
            .apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent for the action button to kill the service
        val stopIntent = Intent(this, MediaPlayerService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create the action button
        val stopAction = NotificationCompat.Action.Builder(
            R.drawable.ic_close,
            getString(R.string.stop),
            stopPendingIntent
        ).build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.mend_media_player_active))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(pendingIntent) // Set the content intent to make the whole notification clickable
            .setAutoCancel(false) // This makes the notification disappear when clicked
            .addAction(stopAction)
            .build()
    }

    private fun observeUnlockState() {
        launch(Dispatchers.IO) {
            privateKeyManager.unlocked
                .filter { !it }
                .collect {
                    withContext(Dispatchers.Main) { cleanUp() }
                }
        }
    }

    private fun cleanUp() {
        try {
            if (exoPlayer.availableCommands.contains(ExoPlayer.COMMAND_STOP)) exoPlayer.stop()
            if (exoPlayer.availableCommands.contains(ExoPlayer.COMMAND_GET_TRACKS)) exoPlayer.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (exoPlayer.availableCommands.contains(ExoPlayer.COMMAND_GET_TRACKS)) exoPlayer.release()
    }
}
