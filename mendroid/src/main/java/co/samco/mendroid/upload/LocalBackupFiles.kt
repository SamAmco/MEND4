package co.samco.mendroid.upload

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class WriteMode { REPLACE, APPEND }

interface LocalBackupFiles {
    fun openInput(file: LocalBackupFile): InputStream
    fun openOutput(file: LocalBackupFile): OutputStream
    /** Seekable local files only. Failed appends restore their original length before returning. */
    suspend fun append(file: LocalBackupFile, write: suspend (OutputStream) -> Unit)
    fun newSnapshot(): File
}

@Singleton
class AndroidBackupFiles @Inject constructor(@ApplicationContext private val context: Context) : LocalBackupFiles {
    private val snapshots by lazy {
        File(context.noBackupFilesDir, "upload-snapshots").apply {
            // First snapshot access in this process: no live snapshot can exist yet.
            deleteRecursively()
            check(mkdirs())
        }
    }
    override fun openInput(file: LocalBackupFile) = requireNotNull(context.contentResolver.openInputStream(file.uri.toUri()))
    override fun openOutput(file: LocalBackupFile) =
        requireNotNull(context.contentResolver.openOutputStream(file.uri.toUri(), "w"))

    override suspend fun append(file: LocalBackupFile, write: suspend (OutputStream) -> Unit) {
        val descriptor = requireNotNull(context.contentResolver.openFileDescriptor(file.uri.toUri(), "rw"))
        ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { appendRestoringOnFailure(it, write) }
    }
    override fun newSnapshot(): File = File.createTempFile("encrypted-", ".snapshot", snapshots)
}

/** Shared by local writers and snapshot preparation, never held during network I/O. */
@Singleton
class LocalBackupLock @Inject constructor() {
    private val mutex = Mutex()
    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}

interface UploadWakeup {
    /** Returns after WorkManager has persisted the request, if uploads are enabled. */
    suspend fun requestUpload()
    suspend fun rescheduleUploads()
}

/** Appends never overwrite existing bytes, so truncation restores the previous contents without a backup copy. */
internal suspend fun appendRestoringOnFailure(output: FileOutputStream, write: suspend (OutputStream) -> Unit) {
    val channel = output.channel
    val originalLength = channel.size()
    channel.position(originalLength)
    try {
        write(output)
        channel.force(true)
    } catch (failure: Throwable) {
        // Synchronous rollback also runs when the writing coroutine was cancelled.
        try {
            channel.truncate(originalLength)
            channel.force(true)
        } catch (rollback: Throwable) {
            failure.addSuppressed(rollback)
        }
        throw failure
    }
}
