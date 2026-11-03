package co.samco.mendroid.upload

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/** The only component that knows the credential file format or Android Keystore. */
@Singleton
class EncryptedCredentialStore @Inject constructor(@ApplicationContext context: Context) : CredentialStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, "nextcloud-account"))
    private var cached: CredentialRead? = null

    @Synchronized
    override fun read(): CredentialRead = cached ?: load().also { cached = it }

    private fun load(): CredentialRead = try {
        val stored = JSONObject(file.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, decode(stored.getString("iv"))))
        }
        val json = JSONObject(cipher.doFinal(decode(stored.getString("data"))).toString(Charsets.UTF_8))
        CredentialRead.Available(NextcloudSession(
            NextcloudAccount(json.getString("server"), json.getString("userId")),
            json.getString("login"), json.getString("password")))
    } catch (_: FileNotFoundException) {
        CredentialRead.Missing
    } catch (_: Exception) {
        CredentialRead.Unavailable
    }

    @Synchronized
    override fun save(session: NextcloudSession) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val json = JSONObject().put("server", session.account.server).put("userId", session.account.userId)
            .put("login", session.login).put("password", session.appPassword)
        val stored = JSONObject().put("iv", encode(cipher.iv))
            .put("data", encode(cipher.doFinal(json.toString().toByteArray())))
        val stream = file.startWrite()
        try {
            stream.write(stored.toString().toByteArray())
            file.finishWrite(stream)
            cached = CredentialRead.Available(session)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }

    @Synchronized
    override fun clear() { file.delete(); cached = CredentialRead.Missing }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getKey(KEY_ALIAS, null) as? SecretKey ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            }.generateKey()
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(value: String) = Base64.decode(value, Base64.NO_WRAP)

    private companion object {
        const val KEY_ALIAS = "nextcloud-account"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
