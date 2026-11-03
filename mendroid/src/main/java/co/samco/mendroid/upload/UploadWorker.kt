package co.samco.mendroid.upload

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import co.samco.mendroid.MainActivity
import co.samco.mendroid.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UploadEntryPoint {
    fun runner(): UploadRunner
    fun scheduler(): UploadScheduler
    fun queue(): UploadQueue
    fun failures(): UploadFailureNotifications
}

/** WorkManager adaptation only; transfer behavior is independently testable in UploadRunner. */
class UploadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val services = EntryPointAccessors.fromApplication(applicationContext, UploadEntryPoint::class.java)
        val runner = services.runner()
        when (runner.run { setForeground(getForegroundInfo()) }) {
            RunResult.RETRY -> Result.retry()
            RunResult.COMPLETE, RunResult.WAITING -> Result.success()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // The queue itself may be temporarily unavailable, so there may be nowhere to record an error.
        EntryPointAccessors.fromApplication(applicationContext, UploadEntryPoint::class.java).failures().showFailure()
        Result.retry()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val channel = "nextcloud-uploads"
        applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channel, applicationContext.getString(R.string.nextcloud_uploads), NotificationManager.IMPORTANCE_LOW))
        val intent = PendingIntent.getActivity(applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(applicationContext, channel)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentTitle(applicationContext.getString(R.string.nextcloud_uploads))
            .setContentText(applicationContext.getString(R.string.upload_in_progress))
            .setContentIntent(intent).setOngoing(true).build()
        return ForegroundInfo(103, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}

/** Prepare a manual retry separately so database failures cannot consume the user's retry action. */
class UploadRetryWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        EntryPointAccessors.fromApplication(applicationContext, UploadEntryPoint::class.java).queue().retryFailures()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        EntryPointAccessors.fromApplication(applicationContext, UploadEntryPoint::class.java).failures().showFailure()
        Result.retry()
    }
}

class UploadWakeWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val reason = inputData.getString("reason")?.let(WakeReason::valueOf) ?: WakeReason.RECOVERY
        EntryPointAccessors.fromApplication(applicationContext, UploadEntryPoint::class.java).scheduler().wake(reason)
        return Result.success()
    }
}
