package co.samco.mendroid.nextcloud

import android.app.Application
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.Protocol
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class NextcloudCertificatesTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var directory: File
    private lateinit var certificates: NextcloudCertificates
    private lateinit var http: NextcloudHttp
    private val servers = mutableListOf<MockWebServer>()
    @Before fun setup() {
        directory = temporary.newFolder()
        certificates = NextcloudCertificates(directory)
        http = NextcloudHttp(certificates)
    }
    @After fun teardown() { servers.forEach { it.shutdown() } }
    private fun certificate() = HeldCertificate.Builder().addSubjectAlternativeName("localhost")
        .addSubjectAlternativeName("127.0.0.1").build()
    private fun server(certificate: HeldCertificate = certificate(), port: Int = 0) = MockWebServer().apply {
        val tls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        protocols = listOf(Protocol.HTTP_1_1)
        useHttps(tls.sslSocketFactory(), false)
        start(port)
        servers.add(this)
    }
    private fun request(server: MockWebServer, approve: Boolean = false) = Request.Builder().url(server.url("/status.php"))
        .apply { if (approve) tag(TrustSelfSignedOnConnect::class.java, TrustSelfSignedOnConnect) }.build()
    private suspend fun rejects(request: Request, client: NextcloudHttp = http) {
        try { client.request(request); fail("Unapproved or invalid certificate must be rejected") }
        catch (e: NextcloudException) { assertEquals(NextcloudFailure.CERTIFICATE, e.reason) }
    }

    @Test fun selfSignedCertificateIsRejectedBeforeSendingAnyHttpRequest() = runBlocking {
        val server = server()
        rejects(request(server))
        assertEquals(0, server.requestCount)
        assertFalse(File(directory, "nextcloud-certificates").exists())
    }

    @Test fun connectCanApproveACertificateAndLaterRequestsReuseItAfterRestart() = runBlocking {
        val server = server()
        repeat(2) { server.enqueue(MockResponse().setBody("Nextcloud").setHeader("Connection", "close")) }
        assertEquals("Nextcloud", http.request(request(server, approve = true)))
        val restarted = NextcloudHttp(NextcloudCertificates(directory))
        assertEquals("Nextcloud", restarted.request(request(server)))
        assertEquals(2, server.requestCount)
    }

    @Test fun approvalDoesNotTrustTheSameCertificateOnAnotherPortOrHostname() = runBlocking {
        val certificate = certificate()
        val first = server(certificate)
        first.enqueue(MockResponse().setBody("Nextcloud"))
        http.request(request(first, approve = true))
        val otherPort = server(certificate)
        rejects(request(otherPort))
        val otherHost = request(first).newBuilder().url(first.url("/status.php").newBuilder().host("127.0.0.1").build()).build()
        rejects(otherHost)
        assertEquals(0, otherPort.requestCount)
        assertEquals(1, first.requestCount)
    }

    @Test fun aChangedSelfSignedCertificateRequiresANewConnectApproval() = runBlocking {
        val old = server()
        old.enqueue(MockResponse().setBody("old").setHeader("Connection", "close"))
        http.request(request(old, approve = true))
        val replacement = HandshakeCertificates.Builder().heldCertificate(certificate()).build()
        old.useHttps(replacement.sslSocketFactory(), false)
        rejects(request(old))
        old.enqueue(MockResponse().setBody("new"))
        assertEquals("new", http.request(request(old, approve = true)))
    }

    @Test fun approvalStillRejectsWrongHostnamesAndDoesNotSaveThem() = runBlocking {
        val wrongHost = HeldCertificate.Builder().addSubjectAlternativeName("wrong.example").build()
        val server = server(wrongHost)
        rejects(request(server, approve = true))
        assertFalse(File(directory, "nextcloud-certificates").exists())
        assertEquals(0, server.requestCount)
    }

    @Test fun approvalStillRejectsExpiredCertificates() = runBlocking {
        val now = System.currentTimeMillis()
        val expired = HeldCertificate.Builder().addSubjectAlternativeName("localhost")
            .validityInterval(now - TimeUnit.DAYS.toMillis(2), now - TimeUnit.DAYS.toMillis(1)).build()
        val server = server(expired)
        rejects(request(server, approve = true))
        assertFalse(File(directory, "nextcloud-certificates").exists())
        assertEquals(0, server.requestCount)
    }

    @Test fun loginApprovalIsCarriedThroughTheRealHandshake() = runBlocking {
        val server = server()
        val root = server.url("/").toString().trimEnd('/')
        server.enqueue(MockResponse().setBody("""{"login":"$root/login","poll":{"endpoint":"$root/poll","token":"poll-secret"}}"""))
        val login = NextcloudLoginClient(http).begin(root, trustSelfSigned = true)
        assertEquals("$root/login", login.browserUrl)
        assertEquals("POST", server.takeRequest().method)
    }
}
