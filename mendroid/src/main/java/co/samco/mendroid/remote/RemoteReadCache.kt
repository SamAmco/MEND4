package co.samco.mendroid.remote

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job

/** Encrypted downloads belong to one unlock session. Locking cancels readers and deletes their files. */
@Singleton
class RemoteReadCache internal constructor(private val directory: File) {
    @Inject constructor(@ApplicationContext context: Context) : this(File(context.cacheDir, "nextcloud-reads"))

    class Lease internal constructor(internal val generation: Long)
    private val lock = Any()
    private var generation = 0L
    private var unlocked = false
    private val readers = mutableSetOf<Job>()

    fun openSession() {
        clear()
        synchronized(lock) { unlocked = true }
    }

    fun clear() {
        val cancelled = synchronized(lock) {
            unlocked = false
            generation++
            directory.deleteRecursively()
            readers.toList().also { readers.clear() }
        }
        cancelled.forEach { it.cancel(CancellationException("MEND locked or read session replaced")) }
    }

    suspend fun <T> withSession(block: suspend (Lease) -> T): T {
        val job = currentCoroutineContext().job
        val lease = synchronized(lock) {
            checkUnlocked()
            readers.add(job)
            Lease(generation)
        }
        try {
            val result = block(lease)
            synchronized(lock) { checkCurrent(lease) }
            return result
        } finally {
            synchronized(lock) { readers.remove(job) }
        }
    }

    suspend fun download(lease: Lease, write: suspend (OutputStream) -> Unit): File {
        val (file, output) = synchronized(lock) {
            checkCurrent(lease)
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create download cache")
            val file = File.createTempFile("encrypted-", ".cache", directory)
            try { file to file.outputStream() }
            catch (e: IOException) { file.delete(); throw e }
        }
        try {
            output.use { write(it) }
            synchronized(lock) { checkCurrent(lease) }
            return file
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    private fun checkUnlocked() {
        if (!unlocked) throw CancellationException("MEND is locked")
    }
    private fun checkCurrent(lease: Lease) {
        checkUnlocked()
        if (lease.generation != generation) throw CancellationException("Read session expired")
    }
}
