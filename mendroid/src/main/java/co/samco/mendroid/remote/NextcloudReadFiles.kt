package co.samco.mendroid.remote

import co.samco.mendroid.nextcloud.AccountId
import co.samco.mendroid.nextcloud.CredentialRead
import co.samco.mendroid.nextcloud.CredentialStore
import co.samco.mendroid.nextcloud.NextcloudException
import co.samco.mendroid.nextcloud.NextcloudFailure
import co.samco.mendroid.nextcloud.NextcloudSession
import co.samco.mendroid.nextcloud.NextcloudSettings
import co.samco.mendroid.nextcloud.RemotePath
import java.io.File
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RemoteDirectoryEntry(val path: RemotePath, val directory: Boolean)
data class RemoteReadFile(val account: AccountId, val path: RemotePath) {
    val name: String get() = path.name
}
data class RemoteReadProblem(val folder: RemotePath, val reason: NextcloudFailure)
data class RemoteLogListing(val files: List<RemoteReadFile>, val problems: List<RemoteReadProblem>)

interface RemoteReadClient {
    suspend fun children(session: NextcloudSession, folder: RemotePath): List<RemoteDirectoryEntry>
    suspend fun download(session: NextcloudSession, file: RemotePath, output: OutputStream)
}

/** Read-only sources: immediate children only, bound to the connected account and selected folders. */
class NextcloudReadFiles @Inject constructor(
    private val settings: NextcloudSettings,
    private val credentials: CredentialStore,
    private val client: RemoteReadClient,
    private val cache: RemoteReadCache
) {
    val config = settings.config

    suspend fun logs(): RemoteLogListing = withContext(Dispatchers.IO) {
        cache.withSession {
            val folders = config.value.additionalFolders
            if (folders.isEmpty()) return@withSession RemoteLogListing(emptyList(), emptyList())
            val session = session()
            val files = mutableListOf<RemoteReadFile>()
            val problems = mutableListOf<RemoteReadProblem>()
            for (folder in folders) {
                try {
                    files += children(session, folder).filter { !it.directory && it.path.name.endsWith(".mend") }
                        .map { RemoteReadFile(session.account.id, it.path) }
                } catch (e: CancellationException) { throw e }
                catch (e: IOException) { problems += RemoteReadProblem(folder, reason(e)) }
            }
            RemoteLogListing(files.distinct().sortedBy { it.path.value }, problems)
        }
    }

    suspend fun open(file: RemoteReadFile): File = withContext(Dispatchers.IO) {
        cache.withSession { lease ->
            val session = session()
            download(session, file, lease)
        }
    }

    suspend fun findAttachment(name: String): File? = withContext(Dispatchers.IO) {
        require(name.endsWith(".enc") && '/' !in name && '\\' !in name)
        cache.withSession { lease ->
            val folders = config.value.additionalFolders
            if (folders.isEmpty()) return@withSession null
            val session = session()
            var failure: IOException? = null
            for (folder in folders) {
                try {
                    val match = children(session, folder).firstOrNull { !it.directory && it.path.name == name } ?: continue
                    val file = RemoteReadFile(session.account.id, match.path)
                    return@withSession download(session, file, lease)
                } catch (e: CancellationException) { throw e }
                catch (e: IOException) { failure = e }
            }
            failure?.let { throw it }
            null
        }
    }

    private suspend fun download(session: NextcloudSession, file: RemoteReadFile, lease: RemoteReadCache.Lease): File {
        requireAllowed(session, file)
        val downloaded = cache.download(lease) { client.download(session, file.path, it) }
        try {
            requireAllowed(session, file)
            return downloaded
        } catch (e: Exception) {
            downloaded.delete()
            throw e
        }
    }

    private suspend fun children(session: NextcloudSession, folder: RemotePath): List<RemoteDirectoryEntry> {
        requireFolder(session, folder)
        val result = client.children(session, folder)
        requireFolder(session, folder)
        return result.filter { it.path != folder && it.path.parent == folder }
    }

    private fun session(): NextcloudSession {
        val session = (credentials.read() as? CredentialRead.Available)?.session
        if (session == null || session.account.id != config.value.account?.id) throw NextcloudException(NextcloudFailure.ACCOUNT_UNAVAILABLE)
        return session
    }

    private fun requireAllowed(session: NextcloudSession, file: RemoteReadFile) {
        if (file.account != session.account.id || config.value.account?.id != file.account)
            throw NextcloudException(NextcloudFailure.ACCOUNT_UNAVAILABLE)
        requireFolder(session, file.path.parent)
    }
    private fun requireFolder(session: NextcloudSession, folder: RemotePath) {
        if (config.value.account?.id != session.account.id) throw NextcloudException(NextcloudFailure.ACCOUNT_UNAVAILABLE)
        if (folder !in config.value.additionalFolders) throw NextcloudException(NextcloudFailure.DESTINATION_MISSING)
    }
    private fun reason(error: IOException) = (error as? NextcloudException)?.reason ?: NextcloudFailure.CONNECTION
}
