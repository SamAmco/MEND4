package co.samco.mendroid.upload

import co.samco.mendroid.remote.RemoteDirectoryEntry
import co.samco.mendroid.remote.RemoteReadClient
import java.io.OutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.xmlpull.v1.XmlPullParserException

/** A completed temporary upload. The runner owns the decision to publish it. */
interface StagedUpload {
    suspend fun publish(policy: OverwritePolicy)
    suspend fun discard()
}

interface UploadTransport {
    suspend fun stage(session: NextcloudSession, path: RemotePath, file: File, progress: (Long) -> Unit): StagedUpload
}

interface RemoteFolderBrowser {
    suspend fun folders(session: NextcloudSession, path: RemotePath): List<RemoteFolder>
}

class WebDavClient @Inject constructor(private val http: NextcloudHttp) : UploadTransport, RemoteFolderBrowser, RemoteReadClient {
    internal fun fileUrl(account: NextcloudAccount, path: RemotePath): HttpUrl =
        "${account.server}/remote.php/dav/files/".toHttpUrl().newBuilder().addPathSegment(account.userId).apply {
            path.value.split('/').filter(String::isNotEmpty).forEach { addPathSegment(it) }
        }.build()

    override suspend fun folders(session: NextcloudSession, path: RemotePath): List<RemoteFolder> =
        children(session, path).filter { it.directory }.map { RemoteFolder(it.path.name, it.path) }

    override suspend fun children(session: NextcloudSession, folder: RemotePath): List<RemoteDirectoryEntry> = withContext(Dispatchers.IO) {
        val root = fileUrl(session.account, folder).newBuilder().addPathSegment("").build()
        val xml = """<d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
        val response = http.request(http.authorized(session, root).header("Depth", "1")
            .method("PROPFIND", xml.toRequestBody("application/xml".toMediaType())).build())
        try {
            WebDavListing().parse(root, folder, response)
        } catch (e: XmlPullParserException) {
            throw UploadException(UploadFailure.INVALID_RESPONSE).apply { initCause(e) }
        }
    }

    override suspend fun download(session: NextcloudSession, file: RemotePath, output: OutputStream) {
        http.response(http.authorized(session, fileUrl(session.account, file)).get().build()) {
            http.checkStatus(it.code)
            it.body.byteStream().use { input -> input.copyTo(output) }
        }
    }

    override suspend fun stage(session: NextcloudSession, path: RemotePath, file: File, progress: (Long) -> Unit): StagedUpload {
        val target = fileUrl(session.account, path)
        val temporary = target.newBuilder().setPathSegment(target.pathSize - 1, ".mend-upload-${UUID.randomUUID()}").build()
        val staged = RemoteStage(session, target, temporary, file)
        try {
            http.request(http.authorized(session, temporary).put(fileBody(file, progress)).build())
            return staged
        } catch (e: Exception) {
            staged.discard()
            throw e
        }
    }

    private fun fileBody(file: File, progress: (Long) -> Unit) = object : RequestBody() {
        override fun contentType() = "application/octet-stream".toMediaType()
        override fun contentLength() = file.length()
        override fun writeTo(sink: BufferedSink) {
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                var sent = 0L
                while (true) {
                    val size = input.read(buffer)
                    if (size == -1) break
                    sink.write(buffer, 0, size)
                    sent += size
                    progress(sent)
                }
            }
        }
    }

    private inner class RemoteStage(
        val session: NextcloudSession, val target: HttpUrl, val temporary: HttpUrl, val file: File
    ) : StagedUpload {
        override suspend fun publish(policy: OverwritePolicy) {
            try {
                http.request(http.authorized(session, temporary).header("Destination", target.toString())
                    .header("Overwrite", if (policy == OverwritePolicy.REPLACE) "T" else "F").method("MOVE", null).build())
            } catch (e: UploadException) {
                if (policy != OverwritePolicy.REQUIRE_IDENTICAL || e.statusCode != 412 || !existingMatches()) throw e
            }
        }

        private suspend fun existingMatches(): Boolean = http.response(http.authorized(session, target).get().build()) {
            if (!it.isSuccessful) false else it.body.byteStream().use { remote ->
                file.inputStream().use { local -> streamsMatch(local, remote) }
            }
        }

        override suspend fun discard() = withContext(NonCancellable) {
            // Bounded best effort: losing a connection can leave an encrypted temporary sibling.
            try { http.request(http.authorized(session, temporary).delete().build(), timeoutMillis = 5000) }
            catch (_: IOException) { }
            Unit
        }
    }

    private fun streamsMatch(local: InputStream, remote: InputStream): Boolean {
        val a = ByteArray(64 * 1024)
        val b = ByteArray(64 * 1024)
        while (true) {
            val count = local.read(a)
            if (count == -1) return remote.read() == -1
            var offset = 0
            while (offset < count) {
                val read = remote.read(b, offset, count - offset)
                if (read == -1) return false
                offset += read
            }
            if (!(0 until count).all { a[it] == b[it] }) return false
        }
    }
}
