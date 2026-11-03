package co.samco.mendroid.nextcloud

import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Shared HTTP mechanics; no login orchestration, XML parsing, or upload policy. */
@Singleton
class NextcloudHttp internal constructor(
    private val client: OkHttpClient = defaultClient(),
    private val certificates: NextcloudCertificates? = null
) {
    @Inject constructor(certificates: NextcloudCertificates) : this(defaultClient(), certificates)

    private companion object {
        fun defaultClient() = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS).callTimeout(8, TimeUnit.MINUTES).build()
    }

    fun serverUrl(value: String): String {
        val address = value.trim().let { if ("://" in it) it else "https://$it" }
        val url = address.trimEnd('/').toHttpUrl()
        require(url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null)
        return url.toString().trimEnd('/')
    }

    fun sameOrigin(a: HttpUrl, b: HttpUrl) = a.scheme == b.scheme && a.host == b.host && a.port == b.port

    fun authorized(session: NextcloudSession, url: HttpUrl): Request.Builder {
        require(sameOrigin(session.account.server.toHttpUrl(), url))
        return Request.Builder().url(url)
            .header("Authorization", Credentials.basic(session.login, session.appPassword, Charsets.UTF_8))
            .header("User-Agent", "MEND Android")
    }

    suspend fun request(request: Request, timeoutMillis: Long? = null): String = response(request, timeoutMillis) {
        checkStatus(it.code)
        it.body.string()
    }

    suspend fun <T> response(request: Request, timeoutMillis: Long? = null, read: (Response) -> T): T = withContext(Dispatchers.IO) {
        try {
            execute(certificates?.client(client, request.url) ?: client, request, timeoutMillis, read)
        } catch (untrusted: UntrustedSelfSignedCertificate) {
            val trust = certificates
            if (trust == null || request.tag(TrustSelfSignedOnConnect::class.java) == null)
                throw NextcloudException(NextcloudFailure.CERTIFICATE).apply { initCause(untrusted) }
            try {
                execute(trust.client(client, request.url, untrusted.certificate), request, timeoutMillis) { response ->
                    // Save only after TLS and hostname verification succeeded, before returning any credentials.
                    trust.remember(request.url, untrusted.certificate)
                    read(response)
                }
            } catch (e: SSLException) {
                throw NextcloudException(NextcloudFailure.CERTIFICATE).apply { initCause(e) }
            } catch (e: UntrustedSelfSignedCertificate) {
                throw NextcloudException(NextcloudFailure.CERTIFICATE).apply { initCause(e) }
            }
        } catch (e: SSLException) {
            throw NextcloudException(NextcloudFailure.CERTIFICATE).apply { initCause(e) }
        }
    }

    private suspend fun <T> execute(client: OkHttpClient, request: Request, timeoutMillis: Long?, read: (Response) -> T): T =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            timeoutMillis?.let { call.timeout().timeout(it, TimeUnit.MILLISECONDS) }
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWith(Result.failure(certificateFailure(e) ?: e))
                }
                override fun onResponse(call: Call, response: Response) {
                    continuation.resumeWith(runCatching { response.use(read) })
                }
            })
        }

    // Alternate IP attempts can leave the TLS failure suppressed under a connection error.
    private fun certificateFailure(error: IOException): Throwable? {
        val remaining = ArrayDeque<Throwable>().apply { add(error) }
        val visited = mutableSetOf<Throwable>()
        var tlsFailure: SSLException? = null
        while (remaining.isNotEmpty()) {
            val failure = remaining.removeFirst()
            if (!visited.add(failure)) continue
            if (failure is UntrustedSelfSignedCertificate) return failure
            if (failure is SSLException) tlsFailure = failure
            failure.cause?.let(remaining::add)
            remaining.addAll(failure.suppressed)
        }
        return tlsFailure
    }

    fun checkStatus(code: Int) {
        if (code in 200..299) return
        val reason = when (code) {
            401 -> NextcloudFailure.AUTHENTICATION
            403 -> NextcloudFailure.ACCESS_DENIED
            404, 409 -> NextcloudFailure.DESTINATION_MISSING
            412 -> NextcloudFailure.REMOTE_CONFLICT
            413 -> NextcloudFailure.TOO_LARGE
            507 -> NextcloudFailure.STORAGE_FULL
            408, 423, 429, in 500..599 -> NextcloudFailure.SERVER_BUSY
            in 300..399 -> NextcloudFailure.REDIRECT
            else -> NextcloudFailure.SERVER_REJECTED
        }
        throw NextcloudException(reason, code)
    }
}
