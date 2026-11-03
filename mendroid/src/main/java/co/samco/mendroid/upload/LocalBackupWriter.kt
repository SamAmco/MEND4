package co.samco.mendroid.upload

import co.samco.mendroid.nextcloud.NextcloudSettings
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@Singleton
class LocalBackupWriter @Inject constructor(
    private val queue: UploadQueue,
    private val files: LocalBackupFiles,
    private val lock: LocalBackupLock,
    private val settings: NextcloudSettings,
    private val wakeup: UploadWakeup
) {
    /** This component owns stream closure; backup bookkeeping never turns a save into a failure. */
    suspend fun write(file: LocalBackupFile, mode: WriteMode, encrypt: suspend (OutputStream) -> Unit): LocalSaveResult =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val target = settings.config.value.target(file)
                val token = target?.let {
                    tryQueue {
                        queue.recoverAfterRestart()
                        queue.beginWrite(file, it)
                    }
                }
                try {
                    when (mode) {
                        WriteMode.APPEND -> files.append(file, encrypt)
                        WriteMode.REPLACE -> files.openOutput(file).use { encrypt(it) }
                    }
                } catch (e: Exception) {
                    withContext(NonCancellable) { token?.let { tryQueue { queue.failWrite(it) } } }
                    throw e
                }
                // Once the stream closed successfully, finish queue registration and await durable scheduling.
                val registration = withContext(NonCancellable) {
                    val result = when {
                        target == null -> BackupRegistration.NOT_CONFIGURED
                        token == null -> BackupRegistration.NEEDS_ATTENTION
                        tryQueue { queue.completeWrite(token) } != Transition.APPLIED -> {
                            tryQueue { queue.registrationFailed(token) }
                            BackupRegistration.NEEDS_ATTENTION
                        }
                        else -> BackupRegistration.QUEUED
                    }
                    if (result == BackupRegistration.QUEUED) requestWakeup()
                    result
                }
                if (registration == BackupRegistration.NEEDS_ATTENTION) {
                    try { settings.recordUnqueuedSave() } catch (_: Exception) { /* The caller still receives the warning result. */ }
                }
                LocalSaveResult(registration)
            }
        }

    /** Checks protection under the write lock. Only explicit verification may override the current failed revision. */
    suspend fun enqueueExisting(
        file: LocalBackupFile,
        target: UploadTarget? = settings.config.value.target(file),
        verifiedRevision: UploadRevision? = null
    ): BackupRegistration = withContext(Dispatchers.IO) {
        lock.withLock {
            if (target == null) return@withLock BackupRegistration.NOT_CONFIGURED
            val queued = tryQueue {
                queue.recoverAfterRestart()
                val protected = queue.allEntries().filter { it.file.uri == file.uri && it.protectsLocalFile }
                if (protected.isNotEmpty() && protected.none { it.revision == verifiedRevision }) return@tryQueue false
                queue.enqueueExisting(file, target)
                true
            } ?: return@withLock BackupRegistration.NEEDS_ATTENTION
            if (!queued) return@withLock BackupRegistration.SKIPPED
            requestWakeup()
            BackupRegistration.QUEUED
        }
    }

    private suspend fun requestWakeup() {
        // A scheduling failure must not turn a successful local save into a failure. Startup retries pending work.
        try { wakeup.requestUpload() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { }
    }

    private suspend fun <T> tryQueue(block: suspend () -> T): T? = try { block() }
    catch (e: CancellationException) { throw e }
    catch (_: Exception) { null }
}
