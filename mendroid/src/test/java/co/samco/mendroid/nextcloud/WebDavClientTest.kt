package co.samco.mendroid.nextcloud

import android.app.Application
import co.samco.mendroid.remote.RemoteDirectoryEntry
import co.samco.mendroid.upload.OverwritePolicy
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class WebDavClientTest {
    private lateinit var server: MockWebServer
    private lateinit var file: File
    private val client = WebDavClient(NextcloudHttp())
    @Before fun setup() {
        server = MockWebServer().apply { start() }
        file = Files.createTempFile("ciphertext-", ".enc").toFile().apply { writeText("encrypted-content") }
    }
    @After fun teardown() { server.shutdown(); file.delete() }
    private fun session() = NextcloudSession(NextcloudAccount(server.url("/nextcloud").toString().trimEnd('/'), "actual-user"), "login@example.com", "secret")
    private suspend fun upload(path: String, policy: OverwritePolicy) {
        val staged = client.stage(session(), RemotePath.of(path), file) { }
        try { staged.publish(policy) } finally { staged.discard() }
    }

    @Test fun completeCiphertextIsStagedBeforePublishingLogReplacement() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(201)) }
        upload("/logs/phone.mend", OverwritePolicy.REPLACE)
        val put = server.takeRequest()
        val move = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("encrypted-content", put.body.readUtf8())
        assertTrue(put.path.orEmpty().contains("/nextcloud/remote.php/dav/files/actual-user/logs/.mend-upload-"))
        assertEquals("MOVE", move.method)
        assertEquals("T", move.getHeader("Overwrite"))
        assertTrue(move.getHeader("Destination").orEmpty().endsWith("/logs/phone.mend"))
        assertEquals("DELETE", server.takeRequest().method)
    }
    @Test fun identicalAttachmentIsAcceptedAfterLostResponse() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(412))
        server.enqueue(MockResponse().setBody("encrypted-content"))
        server.enqueue(MockResponse().setResponseCode(204))
        upload("/enc/123.enc", OverwritePolicy.REQUIRE_IDENTICAL)
        server.takeRequest()
        assertEquals("F", server.takeRequest().getHeader("Overwrite"))
        assertEquals("GET", server.takeRequest().method)
    }
    @Test fun differentAttachmentIsNeverOverwritten() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(412))
        server.enqueue(MockResponse().setBody("different content"))
        server.enqueue(MockResponse().setResponseCode(204))
        try { upload("/enc/123.enc", OverwritePolicy.REQUIRE_IDENTICAL); fail() }
        catch (e: NextcloudException) { assertEquals(NextcloudFailure.REMOTE_CONFLICT, e.reason) }
    }
    @Test fun failuresHaveStructuredReasonsAndRetryPolicies() = runBlocking {
        for ((code, reason) in listOf(401 to NextcloudFailure.AUTHENTICATION, 403 to NextcloudFailure.ACCESS_DENIED,
            507 to NextcloudFailure.STORAGE_FULL, 503 to NextcloudFailure.SERVER_BUSY, 429 to NextcloudFailure.SERVER_BUSY)) {
            server.enqueue(MockResponse().setResponseCode(code))
            server.enqueue(MockResponse().setResponseCode(204))
            try { upload("/enc/a.enc", OverwritePolicy.REQUIRE_IDENTICAL); fail() }
            catch (e: NextcloudException) { assertEquals(reason, e.reason) }
        }
    }
    @Test fun stageCanBeDiscardedWithoutEverPublishing() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(204))
        client.stage(session(), RemotePath.of("/logs/a.mend"), file) { }.discard()
        assertEquals("PUT", server.takeRequest().method)
        assertEquals("DELETE", server.takeRequest().method)
        assertEquals(2, server.requestCount)
    }
    @Test fun credentialsAreNeverForwardedToRedirects() = runBlocking {
        val other = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/steal")))
            server.enqueue(MockResponse().setResponseCode(204))
            try { upload("/enc/a.enc", OverwritePolicy.REQUIRE_IDENTICAL); fail() }
            catch (e: NextcloudException) { assertEquals(NextcloudFailure.REDIRECT, e.reason) }
            assertEquals(0, other.requestCount)
        } finally { other.shutdown() }
    }
    @Test fun folderParserHonorsDavPropertyStatusAndImmediateChildBoundaries() {
        val root = "https://cloud.example/nextcloud/remote.php/dav/files/user/".toHttpUrl()
        fun entry(href: String, status: Int, type: String = "<d:collection/>") = """
            <d:response><d:href>$href</d:href><d:propstat><d:prop>
            <d:resourcetype>$type</d:resourcetype></d:prop>
            <d:status>HTTP/1.1 $status status</d:status></d:propstat></d:response>"""
        val xml = """<d:multistatus xmlns:d="DAV:">
            ${entry(root.toString(), 200)}${entry("${root}a%20b/", 200)}
            ${entry("${root}denied/", 403)}${entry("${root}a/nested/", 200)}
            ${entry("https://evil.example/outside/", 200)}${entry("${root}file.enc", 200, "")}
            </d:multistatus>"""
        assertEquals(listOf(RemoteFolder("a b", RemotePath.of("/a b"))), WebDavListing().parse(root, RemotePath.ROOT, xml).filter { it.directory }.map { RemoteFolder(it.path.name, it.path) })
    }
    @Test fun filesAndCollectionsAreListedWithDepthOneWithoutDescending() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(207).setBody("""<d:multistatus xmlns:d="DAV:">
            <d:response><d:href>/nextcloud/remote.php/dav/files/actual-user/archive/a%20b.mend</d:href>
            <d:propstat><d:prop><d:resourcetype/></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
            <d:response><d:href>/nextcloud/remote.php/dav/files/actual-user/archive/sub/</d:href>
            <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
            <d:response><d:href>/nextcloud/remote.php/dav/files/actual-user/archive/denied.mend</d:href>
            <d:propstat><d:prop><d:resourcetype/></d:prop><d:status>HTTP/1.1 403 Forbidden</d:status></d:propstat></d:response>
            </d:multistatus>"""))
        val entries = client.children(session(), RemotePath.of("/archive"))
        assertEquals(listOf(RemoteDirectoryEntry(RemotePath.of("/archive/a b.mend"), false),
            RemoteDirectoryEntry(RemotePath.of("/archive/sub"), true)), entries)
        val request = server.takeRequest()
        assertEquals("PROPFIND", request.method)
        assertEquals("1", request.getHeader("Depth"))
        assertEquals(1, server.requestCount)
    }

    @Test fun malformedListingHasAStructuredFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(207).setBody("<d:multistatus xmlns:d=\"DAV:\"><d:response>"))
        try { client.children(session(), RemotePath.ROOT); fail() }
        catch (e: NextcloudException) { assertEquals(NextcloudFailure.INVALID_RESPONSE, e.reason) }
    }

    @Test fun downloadStreamsEncryptedBytesUsingOnlyAnAuthenticatedGet() = runBlocking {
        server.enqueue(MockResponse().setBody("encrypted bytes"))
        val output = ByteArrayOutputStream()
        client.download(session(), RemotePath.of("/logs/a b.mend"), output)
        assertEquals("encrypted bytes", output.toString(Charsets.UTF_8))
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertTrue(request.path.orEmpty().endsWith("/logs/a%20b.mend"))
        assertNotNull(request.getHeader("Authorization"))
        assertEquals(1, server.requestCount)
    }

    @Test fun downloadRejectsAuthErrorsWithoutWritingTheErrorBody() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("server error page"))
        val output = ByteArrayOutputStream()
        try { client.download(session(), RemotePath.of("/logs/a.mend"), output); fail() }
        catch (e: NextcloudException) { assertEquals(NextcloudFailure.AUTHENTICATION, e.reason) }
        assertEquals(0, output.size())
    }

    @Test fun pathsAreEncodedOnceAndSetupRequiresHttps() {
        assertTrue(client.fileUrl(session().account, RemotePath.of("/a b/ü.enc")).encodedPath.endsWith("/a%20b/%C3%BC.enc"))
        val http = NextcloudHttp()
        for (url in listOf("http://cloud.example", "https://user:pass@cloud.example", "https://cloud.example/?token=secret")) {
            try { http.serverUrl(url); fail(url) } catch (_: IllegalArgumentException) { }
        }
    }
}
