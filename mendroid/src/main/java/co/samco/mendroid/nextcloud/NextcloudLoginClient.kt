package co.samco.mendroid.nextcloud

import javax.inject.Inject
import kotlinx.coroutines.delay
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class LoginRequest internal constructor(val browserUrl: String, internal val pollUrl: String, internal val token: String)

interface NextcloudLogin {
    suspend fun begin(server: String): LoginRequest
    suspend fun awaitSession(login: LoginRequest): NextcloudSession
}

class NextcloudLoginClient @Inject constructor(private val http: NextcloudHttp) : NextcloudLogin {
    override suspend fun begin(server: String): LoginRequest {
        val url = "${http.serverUrl(server)}/index.php/login/v2".toHttpUrl()
        val json = JSONObject(http.request(Request.Builder().url(url).post(ByteArray(0).toRequestBody()).build()))
        val poll = json.getJSONObject("poll")
        val login = json.getString("login").toHttpUrl()
        val endpoint = poll.getString("endpoint").toHttpUrl()
        require(http.sameOrigin(url, login) && http.sameOrigin(url, endpoint))
        return LoginRequest(login.toString(), endpoint.toString(), poll.getString("token"))
    }

    override suspend fun awaitSession(login: LoginRequest): NextcloudSession {
        repeat(240) {
            val credentials = poll(login)
            if (credentials != null) return resolveUser(credentials, login)
            delay(3000)
        }
        throw NextcloudException(NextcloudFailure.AUTHENTICATION)
    }

    private suspend fun poll(login: LoginRequest): JSONObject? {
        val body = FormBody.Builder().add("token", login.token).build()
        return http.response(Request.Builder().url(login.pollUrl).post(body).build()) {
            if (it.code == 404) null else {
                http.checkStatus(it.code)
                JSONObject(it.body.string())
            }
        }
    }

    private suspend fun resolveUser(json: JSONObject, login: LoginRequest): NextcloudSession {
        val server = http.serverUrl(json.getString("server"))
        require(http.sameOrigin(server.toHttpUrl(), login.pollUrl.toHttpUrl()))
        val provisional = NextcloudSession(NextcloudAccount(server, ""), json.getString("loginName"), json.getString("appPassword"))
        val user = JSONObject(http.request(http.authorized(provisional, "$server/ocs/v2.php/cloud/user?format=json".toHttpUrl())
            .header("OCS-APIRequest", "true").build())).getJSONObject("ocs").getJSONObject("data").getString("id")
        return NextcloudSession(NextcloudAccount(server, user), provisional.login, provisional.appPassword)
    }
}
