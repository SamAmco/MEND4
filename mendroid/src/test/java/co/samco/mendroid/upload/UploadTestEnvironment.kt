package co.samco.mendroid.upload

import android.content.Context
import co.samco.mendroid.nextcloud.NextcloudAccount
import co.samco.mendroid.nextcloud.NextcloudSession
import co.samco.mendroid.nextcloud.NextcloudSettings
import co.samco.mendroid.nextcloud.RemotePath
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking

internal class TestBackupFiles(private val directory: File) : LocalBackupFiles {
    val sources = ConcurrentHashMap<String, File>()
    var outputClosed = false
    var failOutput = false
    val snapshots = mutableListOf<File>()
    fun source(name: String = "phone.mend", content: String = "ciphertext"): LocalBackupFile {
        val file = File.createTempFile("local-", name, directory).apply { writeText(content) }
        sources[file.toURI().toString()] = file
        return LocalBackupFile(file.toURI().toString(), name, if (name.endsWith(".enc")) UploadKind.ATTACHMENT else UploadKind.LOG)
    }
    override fun openInput(file: LocalBackupFile): InputStream = sources[file.uri]?.inputStream() ?: throw IOException("missing")
    override fun openOutput(file: LocalBackupFile, mode: WriteMode): OutputStream {
        if (failOutput) throw IOException("disk full")
        val source = sources[file.uri] ?: throw IOException("missing")
        return object : FileOutputStream(source, mode == WriteMode.APPEND) {
            override fun close() { super.close(); outputClosed = true }
        }
    }
    override fun newSnapshot(): File = File.createTempFile("encrypted-", ".snapshot", directory).also(snapshots::add)
}

internal class TestWakeup : UploadWakeup {
    var requests = 0
    var reschedules = 0
    var onRequest: (suspend () -> Unit)? = null
    override suspend fun requestUpload() { requests++; onRequest?.invoke() }
    override suspend fun rescheduleUploads() { reschedules++; requestUpload() }
}

internal class UploadTestEnvironment(context: Context) {
    val credentials = MemoryCredentials()
    val settings = NextcloudSettings(context, credentials)
    val queue = SqliteUploadQueue(context).also {
        runBlocking { it.recoverAfterRestart() }
    }
    val files = TestBackupFiles(context.cacheDir)
    val lock = LocalBackupLock()
    val wakeup = TestWakeup()
    val writer = LocalBackupWriter(queue, files, lock, settings, wakeup)
    val snapshots = UploadSnapshots(queue, files, lock)
    val session = NextcloudSession(NextcloudAccount("https://cloud.example", "user"), "login", "secret")
    fun connect() {
        settings.connect(session)
        settings.selectAttachmentFolder(RemotePath.of("/enc"))
        settings.selectLogFolder(RemotePath.of("/logs"))
    }
}
