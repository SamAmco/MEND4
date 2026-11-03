package co.samco.mendroid.upload

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class WriteMode(val resolverMode: String) { REPLACE("w"), APPEND("wa") }

interface LocalBackupFiles {
    fun openInput(file: LocalBackupFile): InputStream
    fun openOutput(file: LocalBackupFile, mode: WriteMode): OutputStream
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
    override fun openOutput(file: LocalBackupFile, mode: WriteMode) =
        requireNotNull(context.contentResolver.openOutputStream(file.uri.toUri(), mode.resolverMode))
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
