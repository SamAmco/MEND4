package session

import co.samco.mend4.core.AppProperties
import co.samco.mend4.core.Settings
import co.samco.mend4.core.crypto.impl.DefaultJCECryptoProvider
import co.samco.mend4.desktop.core.ApacheCommonsEncoder
import co.samco.mend4.desktop.dao.SettingsDao
import co.samco.mend4.desktop.dao.impl.OSDaoImpl
import co.samco.mend4.desktop.dao.impl.SettingsImpl
import kotlinx.coroutines.runBlocking
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.security.Security

/** Disposable fixture, never reads the real user's configuration. */
fun main(args: Array<String>) {
    System.setProperty("user.home", args.single())
    Security.addProvider(BouncyCastleProvider())
    val root = File(args.single(), AppProperties.CONFIG_DIR_NAME).apply { mkdirs() }
    val settings = SettingsImpl(OSDaoImpl())
    mapOf(
        Settings.Name.ASYMMETRIC_CIPHER_NAME to "X448",
        Settings.Name.ASYMMETRIC_CIPHER_TRANSFORM to "XIESWithSha256",
        Settings.Name.ASYMMETRIC_KEY_SIZE to "448",
        Settings.Name.PW_KEY_FACTORY_ITERATIONS to "1",
        Settings.Name.PW_KEY_FACTORY_PARALLELISM to "1",
        Settings.Name.PW_KEY_FACTORY_MEMORY_KB to "1024",
        SettingsDao.CURRENT_LOG to "journal",
        SettingsDao.SHRED_COMMAND to "rm -f <filename>",
    ).forEach { (key, value) -> settings.setValue(key, value) }
    listOf(SettingsDao.LOG_DIR, SettingsDao.ENC_DIR, SettingsDao.DEC_DIR).forEach {
        settings.setValue(it, File(root, it.encodedName).apply { mkdirs() }.path)
    }
    val crypto = DefaultJCECryptoProvider(settings, ApacheCommonsEncoder())
    crypto.storeEncryptedKeys("test-password".toCharArray(), crypto.generateKeyPair())
    runBlocking {
        File(settings.getValue(SettingsDao.LOG_DIR), "journal.${AppProperties.LOG_FILE_EXTENSION}").outputStream().use {
            crypto.encryptLogStream("SESSION_TEST_PLAINTEXT", it)
        }
        File(args.single(), "document.enc").outputStream().use {
            crypto.encryptEncStream("BINARY_TEST_CONTENT".byteInputStream(), it, "txt")
        }
    }
}
