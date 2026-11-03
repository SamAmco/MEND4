package co.samco.mendroid.upload

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.await
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** WorkManager schedules durable upload attempts; queue entries and retry policy belong to the upload core. */
@Singleton
class UploadScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: UploadSettings,
    private val failures: UploadFailureNotifications
) : UploadWakeup {
    private val manager get() = WorkManager.getInstance(context)

    fun initialize() {
        failures.start()
        enqueueUpload(ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    fun retryFailedUploads() {
        // This worker requeues eligible failures, then schedules an upload with the current network policy.
        manager.enqueueUniqueWork("nextcloud-manual-retry", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<UploadRetryWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }

    override suspend fun requestUpload() {
        enqueueUpload(ExistingWorkPolicy.APPEND_OR_REPLACE)?.await()
    }

    override suspend fun rescheduleUploads() {
        // Replace stale network constraints/backoff, or cancel transfers when paused/disconnected.
        val operation = enqueueUpload(ExistingWorkPolicy.REPLACE) ?: manager.cancelUniqueWork(UPLOADS)
        operation.await()
    }

    private fun enqueueUpload(policy: ExistingWorkPolicy): Operation? {
        val config = settings.config.value
        if (config.paused || config.account == null) return null
        val network = if (config.mobileData) NetworkType.CONNECTED else NetworkType.UNMETERED
        // Append closes the race between a worker finding no pending entry and a new local write.
        return manager.enqueueUniqueWork(UPLOADS, policy,
            OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }

    private companion object {
        const val UPLOADS = "nextcloud-uploads"
    }
}
