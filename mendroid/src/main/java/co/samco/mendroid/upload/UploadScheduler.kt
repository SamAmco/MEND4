package co.samco.mendroid.upload

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class WakeReason { LOCAL_CHANGE, RECOVERY, UNMETERED_AVAILABLE }

/** Jobs are wake-ups only. Queue entries, revisions and failure policy belong to the upload core. */
@Singleton
class UploadScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: UploadSettings,
    private val failures: UploadFailureNotifications
) : UploadWakeup {
    fun initialize() {
        failures.start()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("nextcloud-recovery", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UploadWakeWorker>(15, TimeUnit.MINUTES).build())
        requestUpload()
    }

    fun retryFailedUploads() {
        // A separate request bypasses the automatic worker's backoff. The runner still serializes transfers.
        WorkManager.getInstance(context).beginUniqueWork("nextcloud-manual-retry", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<UploadRetryWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
            .then(uploadRequest()).enqueue()
    }

    override fun requestUpload() = wake(WakeReason.LOCAL_CHANGE)

    internal fun wake(reason: WakeReason) {
        val config = settings.config.value
        if (config.paused || config.account == null) return
        val manager = WorkManager.getInstance(context)
        if (reason != WakeReason.UNMETERED_AVAILABLE && !config.mobileData) {
            manager.enqueueUniqueWork("nextcloud-unmetered-wake", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<UploadWakeWorker>()
                    .setInputData(workDataOf("reason" to WakeReason.UNMETERED_AVAILABLE.name))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()).build())
        }
        // Append closes the race between a worker finding no pending entry and a new local write.
        manager.enqueueUniqueWork("nextcloud-uploads", ExistingWorkPolicy.APPEND_OR_REPLACE,
            uploadRequest())
    }

    private fun uploadRequest() = OneTimeWorkRequestBuilder<UploadWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
}
