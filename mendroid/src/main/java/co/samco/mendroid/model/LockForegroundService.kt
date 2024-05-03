package co.samco.mendroid.model;

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import co.samco.mendroid.R
import co.samco.mendroid.model.LockBroadcastReceiver.Companion.ACTION_LOCK
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

@AndroidEntryPoint
class LockForegroundService : Service(), CoroutineScope {

    override val coroutineContext: CoroutineContext = Job() + Dispatchers.Main

    companion object {
        private const val CHANNEL_ID = "LockForegroundServiceChannel"
        private const val NOTIFICATION_ID = 123
    }

    @Inject
    lateinit var privateKeyManager: PrivateKeyManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        observeLockState()
        return START_STICKY
    }

    private fun observeLockState() {
        launch {
            privateKeyManager.unlocked
                .filter { !it }
                .collect {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
        }
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

    @SuppressLint("LaunchActivityFromNotification")
    private fun buildNotification(): Notification {
        val lockIntent = Intent(this, LockBroadcastReceiver::class.java)
            .apply { action = ACTION_LOCK }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            lockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Additional action button allows you to lock mend from the lock screen as well
        val stopAction = NotificationCompat.Action.Builder(
            R.drawable.ic_close,
            getString(R.string.lock_mend),
            pendingIntent
        ).build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.mend_is_unlocked_notification_title))
            .setContentText(getString(R.string.tap_here_to_lock))
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(pendingIntent) // Set the content intent to make the whole notification clickable
            .addAction(stopAction)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
