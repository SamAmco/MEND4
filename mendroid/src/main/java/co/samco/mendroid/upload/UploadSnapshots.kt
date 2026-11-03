package co.samco.mendroid.upload

import java.io.Closeable
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class EncryptedSnapshot internal constructor(val token: UploadToken, val file: File) : Closeable {
    override fun close() { file.delete() }
}
sealed interface SnapshotResult {
    data class Ready(val snapshot: EncryptedSnapshot) : SnapshotResult
    data object Superseded : SnapshotResult
    data object Unavailable : SnapshotResult
}

class UploadSnapshots @Inject constructor(
    private val queue: UploadQueue,
    private val files: LocalBackupFiles,
    private val lock: LocalBackupLock
) {
    suspend fun prepare(entry: UploadEntry): SnapshotResult = withContext(Dispatchers.IO) {
        lock.withLock {
            val token = queue.claim(entry.revision) ?: return@withLock SnapshotResult.Superseded
            var snapshot: File? = null
            try {
                val output = files.newSnapshot().also { snapshot = it }
                files.openInput(entry.file).use { input -> output.outputStream().use { input.copyTo(it) } }
                SnapshotResult.Ready(EncryptedSnapshot(token, output))
            } catch (e: CancellationException) {
                snapshot?.delete()
                withContext(NonCancellable) { queue.deferUpload(token) }
                throw e
            } catch (_: Exception) {
                snapshot?.delete()
                queue.failUpload(token, UploadFailure.LOCAL_FILE_UNAVAILABLE)
                SnapshotResult.Unavailable
            }
        }
    }
}
