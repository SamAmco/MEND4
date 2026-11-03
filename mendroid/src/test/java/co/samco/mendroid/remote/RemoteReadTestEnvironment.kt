package co.samco.mendroid.remote

import android.content.Context
import androidx.core.content.edit
import co.samco.mendroid.nextcloud.NextcloudAccount
import co.samco.mendroid.nextcloud.NextcloudException
import co.samco.mendroid.nextcloud.NextcloudFailure
import co.samco.mendroid.nextcloud.NextcloudSession
import co.samco.mendroid.nextcloud.NextcloudSettings
import co.samco.mendroid.nextcloud.RemotePath
import co.samco.mendroid.upload.MemoryCredentials
import java.io.File
import java.io.IOException
import java.io.OutputStream

internal class TestReadClient : RemoteReadClient {
    val listing = mutableMapOf<RemotePath, List<RemoteDirectoryEntry>>()
    val contents = mutableMapOf<RemotePath, String>()
    val listed = mutableListOf<RemotePath>()
    val downloaded = mutableListOf<RemotePath>()
    val errors = mutableMapOf<RemotePath, IOException>()
    var downloadHook: (suspend () -> Unit)? = null
    var listHook: (suspend (RemotePath) -> Unit)? = null
    override suspend fun children(session: NextcloudSession, folder: RemotePath): List<RemoteDirectoryEntry> {
        listed.add(folder)
        listHook?.invoke(folder)
        errors[folder]?.let { throw it }
        return listing[folder].orEmpty()
    }
    override suspend fun download(session: NextcloudSession, file: RemotePath, output: OutputStream) {
        downloaded.add(file)
        downloadHook?.invoke()
        output.write((contents[file] ?: throw NextcloudException(NextcloudFailure.DESTINATION_MISSING)).toByteArray())
    }
}
internal class RemoteReadTestEnvironment(context: Context, val directory: File) {
    val credentials = MemoryCredentials()
    val settings: NextcloudSettings
    val client = TestReadClient()
    val cache = RemoteReadCache(directory)
    val files: NextcloudReadFiles
    val session = NextcloudSession(NextcloudAccount("https://cloud.example", "user"), "login", "secret")
    init {
        context.getSharedPreferences("nextcloud", Context.MODE_PRIVATE).edit { clear() }
        settings = NextcloudSettings(context, credentials)
        settings.connect(session)
        cache.openSession()
        files = NextcloudReadFiles(settings, credentials, client, cache)
    }
    fun folder(path: String) { settings.addAdditionalFolder(RemotePath.of(path)) }
    fun file(path: String, content: String = "encrypted"): RemoteReadFile {
        val remote = RemotePath.of(path)
        client.contents[remote] = content
        client.listing[remote.parent] = client.listing[remote.parent].orEmpty() + RemoteDirectoryEntry(remote, false)
        return RemoteReadFile(session.account.id, remote)
    }
}
