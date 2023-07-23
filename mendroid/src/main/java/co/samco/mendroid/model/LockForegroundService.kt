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
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LockForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "LockForegroundServiceChannel"
        private const val NOTIFICATION_ID = 123
        const val ACTION_LOCK = "USER_LOCK_MEND"
    }

    @Inject
    lateinit var privateKeyManager: PrivateKeyManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        if (intent?.action == ACTION_LOCK) {
            privateKeyManager.onUserLockMend()
            stopSelf()
        }

        return START_STICKY
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
        val lockIntent = Intent(this, LockForegroundService::class.java)
            .apply { action = ACTION_LOCK }

        val pendingIntent = PendingIntent.getService(
            this,
            0,
            lockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MEND is unlocked")
            .setContentText("Tap here to lock")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(pendingIntent) // Set the content intent to make the whole notification clickable
            .setAutoCancel(true) // This makes the notification disappear when clicked
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
