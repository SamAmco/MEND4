package co.samco.mendroid.nextcloud

import android.annotation.SuppressLint
import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.json.JSONObject

/** Only a Connect request carrying this tag may approve a new self-signed certificate. */
internal object TrustSelfSignedOnConnect
internal class UntrustedSelfSignedCertificate(val certificate: X509Certificate) : CertificateException("Self-signed server certificate requires approval")

/** Server-specific certificates approved during Connect, kept outside Android backups. */
@Singleton
class NextcloudCertificates internal constructor(directory: File) {
    @Inject constructor(@ApplicationContext context: Context) : this(context.noBackupFilesDir)

    private val file = AtomicFile(File(directory, "nextcloud-certificates"))
    private val platform = trustManager(null)
    private var pins: Map<String, String>? = null
    private val clients = mutableMapOf<Triple<OkHttpClient, String, String?>, OkHttpClient>()

    @Synchronized
    // Both paths delegate validation to a TrustManagerFactory; neither accepts an unchecked chain.
    @SuppressLint("CustomX509TrustManager")
    internal fun client(base: OkHttpClient, url: HttpUrl, candidate: X509Certificate? = null): OkHttpClient {
        if (!url.isHttps) return base
        val encoded = candidate?.let(::encode) ?: load()[origin(url)]
        val approved = encoded?.let {
            CertificateFactory.getInstance("X.509").generateCertificate(
                Base64.decode(it, Base64.NO_WRAP).inputStream()) as X509Certificate
        }
        val key = Triple(base, origin(url), encoded)
        return clients.getOrPut(key) {
            val privateTrust = approved?.let {
                trustManager(KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                    load(null)
                    setCertificateEntry("server", it)
                })
            }
            val manager = object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> =
                    platform.acceptedIssuers + listOfNotNull(approved).toTypedArray()
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = platform.checkClientTrusted(chain, authType)
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    try {
                        platform.checkServerTrusted(chain, authType)
                    } catch (untrusted: CertificateException) {
                        val leaf = chain.firstOrNull() ?: throw untrusted
                        leaf.checkValidity()
                        if (leaf.subjectX500Principal != leaf.issuerX500Principal ||
                            runCatching { leaf.verify(leaf.publicKey) }.isFailure) throw untrusted
                        if (approved == null || !approved.encoded.contentEquals(leaf.encoded))
                            throw UntrustedSelfSignedCertificate(leaf)
                        requireNotNull(privateTrust).checkServerTrusted(chain, authType)
                    }
                }
            }
            val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(manager), null) }
            // OkHttp still performs its normal hostname verification after certificate validation.
            base.newBuilder().sslSocketFactory(tls.socketFactory, manager).build()
        }
    }

    @Synchronized
    internal fun remember(url: HttpUrl, certificate: X509Certificate) {
        certificate.checkValidity()
        val updated = load() + (origin(url) to encode(certificate))
        val output = file.startWrite()
        try {
            output.write(JSONObject(updated).toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
            pins = updated
        } catch (e: Exception) {
            file.failWrite(output)
            throw e
        }
    }

    private fun load(): Map<String, String> = pins ?: try {
        val json = JSONObject(file.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
        json.keys().asSequence().associateWith(json::getString)
    } catch (_: FileNotFoundException) {
        emptyMap()
    }.also { pins = it }

    private fun origin(url: HttpUrl) = url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString()
    private fun encode(certificate: X509Certificate) = Base64.encodeToString(certificate.encoded, Base64.NO_WRAP)
    private fun trustManager(store: KeyStore?): X509TrustManager =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
            .trustManagers.filterIsInstance<X509TrustManager>().single()
}
