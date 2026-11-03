package co.samco.mendroid.upload

import android.app.Application
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class NextcloudLoginTest {
    private fun client(route: (Request) -> String): NextcloudLoginClient {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(route(chain.request()).toResponseBody()).build()
        }.build()
        return NextcloudLoginClient(NextcloudHttp(http))
    }
    @Test fun documentedHandshakeResolvesActualUserIdWithoutMainPassword() = runBlocking {
        val requests = mutableListOf<Request>()
        val login = client { request ->
            requests.add(request)
            when (request.url.encodedPath) {
                "/index.php/login/v2" -> """{"login":"https://cloud.example/login","poll":{"endpoint":"https://cloud.example/poll","token":"poll-secret"}}"""
                "/poll" -> """{"server":"https://cloud.example","loginName":"email@example.com","appPassword":"app-secret"}"""
                else -> """{"ocs":{"data":{"id":"actual-user"}}}"""
            }
        }
        val request = login.begin("https://cloud.example")
        assertEquals("https://cloud.example/login", request.browserUrl)
        val session = login.awaitSession(request)
        assertEquals("actual-user", session.account.userId)
        assertEquals("email@example.com", session.login)
        assertEquals("app-secret", session.appPassword)
        assertNotNull(requests.last().header("Authorization"))
        assertFalse(session.toString().contains("app-secret"))
    }
    @Test fun foreignPollingEndpointIsRejectedBeforeAnyCredentialExchange() = runBlocking {
        val login = client { """{"login":"https://cloud.example/login","poll":{"endpoint":"https://evil.example/poll","token":"secret"}}""" }
        try { login.begin("https://cloud.example"); fail() } catch (_: IllegalArgumentException) { }
    }
}
