package co.samco.mendroid.upload

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import co.samco.mendroid.MainActivity
import co.samco.mendroid.R
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One queue-owned notification, independent of WorkManager's foreground-service notification. */
@Singleton
class UploadFailureNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queue: UploadQueue
) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() = scope.launch {
        // Load persisted failures before deciding whether to clear an existing notification.
        while (isActive) {
            try {
                queue.pending()
                break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("UploadNotifications", "Cannot load upload failures", e)
                delay(30_000)
            }
        }
        queue.entries.collect { entries ->
            if (entries.any { it.failure != null }) showFailure() else manager.cancel(NOTIFICATION_ID)
        }
    }

    fun showFailure() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL,
            context.getString(R.string.nextcloud_uploads), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val retry = PendingIntent.getBroadcast(context, 0, Intent(context, UploadRetryReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentTitle(context.getString(R.string.upload_failure_notification_title))
            .setContentText(context.getString(R.string.upload_failure_notification_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.upload_failure_notification_text)))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, context.getString(R.string.upload_retry), retry)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL = "nextcloud-upload-failures"
        const val NOTIFICATION_ID = 104
    }
}

/** The receiver only schedules durable work; it performs no disk or network operations. */
class UploadRetryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        EntryPointAccessors.fromApplication(context.applicationContext, UploadEntryPoint::class.java)
            .scheduler().retryFailedUploads()
    }
}
