package co.samco.mendroid.upload

import co.samco.mendroid.nextcloud.NextcloudException
import co.samco.mendroid.nextcloud.NextcloudSession
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Owns transfer ordering and publication decisions. It has no WorkManager or UI dependencies. */
@Singleton
class UploadRunner @Inject constructor(
    private val queue: UploadQueue,
    private val snapshots: UploadSnapshots,
    private val eligibility: UploadEligibility,
    private val transport: UploadTransport
) {
    private val transfers = Mutex()
    private val mutableProgress = MutableStateFlow<UploadProgress?>(null)
    val progress = mutableProgress.asStateFlow()

    suspend fun run(foreground: suspend () -> Unit = {}): RunResult = withContext(Dispatchers.IO) {
        transfers.withLock {
            queue.recoverAfterRestart()
            // No transfer is active now; recover claims left by a failed worker/database operation.
            queue.recoverTransferClaims()
            val attempted = mutableSetOf<UploadRevision>()
            var retry = false
            while (true) {
                val entry = queue.pending().firstOrNull { it.revision !in attempted } ?: break
                attempted.add(entry.revision)
                when (process(entry, foreground)) {
                    RunResult.COMPLETE -> Unit
                    RunResult.WAITING -> return@withLock RunResult.WAITING
                    RunResult.RETRY -> retry = true
                }
            }
            if (retry) RunResult.RETRY else RunResult.COMPLETE
        }
    }

    private suspend fun reject(entry: UploadEntry, failure: UploadFailure) {
        queue.claim(entry.revision)?.let { queue.failUpload(it, failure) }
    }

    private suspend fun process(entry: UploadEntry, foreground: suspend () -> Unit): RunResult {
        val permission = eligibility.check(entry.target.account)
        when (permission) {
            UploadPermission.Waiting -> return RunResult.WAITING
            is UploadPermission.Rejected -> { reject(entry, permission.failure); return RunResult.COMPLETE }
            is UploadPermission.Allowed -> Unit
        }
        if (queue.hasCollision(entry)) {
            reject(entry, UploadFailure.DESTINATION_COLLISION)
            return RunResult.COMPLETE
        }
        val prepared = snapshots.prepare(entry)
        if (prepared !is SnapshotResult.Ready) return RunResult.COMPLETE
        return prepared.snapshot.use { snapshot -> transfer(entry, snapshot, permission.session, foreground) }
    }

    private suspend fun transfer(
        entry: UploadEntry, snapshot: EncryptedSnapshot, session: NextcloudSession, foreground: suspend () -> Unit
    ): RunResult {
        var staged: StagedUpload? = null
        try {
            if (snapshot.file.length() >= 10L * 1024 * 1024) foreground()
            staged = transport.stage(session, entry.target.path, snapshot.file) { sent ->
                mutableProgress.value = UploadProgress(entry.revision,
                    (sent * 100 / snapshot.file.length().coerceAtLeast(1)).toInt())
            }
            if (!queue.isCurrent(snapshot.token)) return RunResult.COMPLETE
            when (val permission = eligibility.check(entry.target.account)) {
                UploadPermission.Waiting -> { queue.deferUpload(snapshot.token); return RunResult.WAITING }
                is UploadPermission.Rejected -> { queue.failUpload(snapshot.token, permission.failure); return RunResult.COMPLETE }
                is UploadPermission.Allowed -> Unit
            }
            staged.publish(entry.file.kind.overwrite)
            queue.completeUpload(snapshot.token)
            return RunResult.COMPLETE
        } catch (e: CancellationException) {
            withContext(NonCancellable) { queue.deferUpload(snapshot.token) }
            throw e
        } catch (e: Exception) {
            val failure = when (e) {
                is NextcloudException -> e.reason.asUploadFailure()
                is UploadException -> e.reason
                is IOException -> UploadFailure.CONNECTION
                else -> UploadFailure.BACKGROUND_UNAVAILABLE
            }
            queue.failUpload(snapshot.token, failure)
            return if (failure.automaticRetry) RunResult.RETRY else RunResult.COMPLETE
        } finally {
            try { withContext(NonCancellable) { staged?.discard() } }
            catch (_: Exception) { /* Remote cleanup must not change the recorded transfer result. */ }
            finally { mutableProgress.value = null }
        }
    }
}
