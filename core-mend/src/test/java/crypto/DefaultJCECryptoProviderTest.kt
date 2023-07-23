package crypto

import co.samco.mend4.core.IBase64EncodingProvider
import co.samco.mend4.core.Settings
import co.samco.mend4.core.crypto.UnlockResult
import co.samco.mend4.core.crypto.impl.DefaultJCECryptoProvider
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.security.InvalidAlgorithmParameterException
import java.security.PrivateKey
import java.security.Security
import java.util.Base64

class DefaultJCECryptoProviderTest {
    private val newLine = System.getProperty("line.separator")

    private val password = "password"

    private val settings = object : Settings {
        private val settingsMap = mutableMapOf<Settings.Name, String>()

        override fun setValue(name: Settings.Name, value: String) {
            settingsMap[name] = value
        }

        override fun getValue(name: Settings.Name): String? {
            return settingsMap[name]
        }

    }
    private val encodingProvider = object : IBase64EncodingProvider {
        override fun decodeBase64(base64String: String): ByteArray {
            return Base64.getUrlDecoder().decode(base64String)
        }

        override fun encodeBase64URLSafeString(data: ByteArray): String {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(data)
        }

    }

    private val uut = DefaultJCECryptoProvider(settings, encodingProvider)

    @Before
    fun before() {
        //Provides more algorithms we can test :)
        // I'm actually only testing ECIES from this provider right now though.
        Security.addProvider(BouncyCastleProvider())

        settings.apply {
            setValue(Settings.Name.PW_KEY_FACTORY_MEMORY_KB, "2")
            setValue(Settings.Name.PW_KEY_FACTORY_PARALLELISM, "1")
            setValue(Settings.Name.PW_KEY_FACTORY_ITERATIONS, "1")
        }
    }

    @Test
    fun `Print out the ciphers etc provided by BountyCastle` () {
        //Not a real test, but handy for debugging
        //Security.getProviders().forEach { println(it) }
        Security.getProvider("BC").services
            .filter {
                it.toString().contains("Cipher", ignoreCase = true)
                        && it.toString().contains("edec", ignoreCase = true)
            }
            .forEach { println(it) }
    }


    @Test
    fun `RSA-ECB-PKCS1Padding-4096`() {
        settings.apply {
            setValue(Settings.Name.ASYMMETRIC_CIPHER_NAME, "RSA")
            setValue(Settings.Name.ASYMMETRIC_CIPHER_TRANSFORM, "RSA/ECB/PKCS1Padding")
            setValue(Settings.Name.ASYMMETRIC_KEY_SIZE, "4096")
        }
        runAllTests()
    }

    @Test
    fun `RSA-ECB-OAEPWithSHA-512AndMGF1Padding-4096`() {
        settings.apply {
            setValue(Settings.Name.ASYMMETRIC_CIPHER_NAME, "RSA")
            setValue(
                Settings.Name.ASYMMETRIC_CIPHER_TRANSFORM,
                "RSA/ECB/OAEPWithSHA-512AndMGF1Padding"
            )
            setValue(Settings.Name.ASYMMETRIC_KEY_SIZE, "4096")
        }
        runAllTests()
    }

    @Test
    fun `RSA-ECB-PKCS1Padding-2048`() {
        settings.apply {
            setValue(Settings.Name.ASYMMETRIC_CIPHER_NAME, "RSA")
            setValue(Settings.Name.ASYMMETRIC_CIPHER_TRANSFORM, "RSA/ECB/PKCS1Padding")
            setValue(Settings.Name.ASYMMETRIC_KEY_SIZE, "2048")
        }
        runAllTests()
    }

    @Test
    fun `RSA-ECB-OAEPWithSHA-256AndMGF1Padding-3072`() {
        settings.apply {
            setValue(Settings.Name.ASYMMETRIC_CIPHER_NAME, "RSA")
            setValue(
                Settings.Name.ASYMMETRIC_CIPHER_TRANSFORM,
                "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
            )
            setValue(Settings.Name.ASYMMETRIC_KEY_SIZE, "3072")
        }
        runAllTests()
    }

    //Not currently supporting XIES/ECIES with more complex transforms
    @Test(expected = InvalidAlgorithmParameterException::class)
    fun `X448-XIESWithAES-CBC 448`() {
        settings.apply {
            setValue(Settings.Name.ASYMMETRIC_CIPHER_NAME, "X448")
            setValue(Settings.Name.ASYMMETRIC_CIPHER_TRANSFORM, "XIESWithAES-CBC")
            setValue(Settings.Name.ASYMMETRIC_KEY_SIZE, "448")
        }
        runAllTests()
    }

    @Test
    fun `X448-XIES 448`() {
        //This seems to crash on JRE 11 but works on 20
        // I was planning on using this as standard but the compatibility issue is pretty bad
        // so I guess i'm going to have to use ECIES until it's fixed.
        settings.apply {
            setValue(Settings.Name.ASYMMETRIC_CIPHER_NAME, "X448")
            setValue(Settings.Name.ASYMMETRIC_CIPHER_TRANSFORM, "XIES")
            setValue(Settings.Name.ASYMMETRIC_KEY_SIZE, "448")
        }
        runAllTests()
    }

    @Test
    fun `EC-ECIES 521`() {
        settings.apply {
            setValue(Settings.Name.ASYMMETRIC_CIPHER_NAME, "EC")
            setValue(Settings.Name.ASYMMETRIC_CIPHER_TRANSFORM, "ECIES")
            setValue(Settings.Name.ASYMMETRIC_KEY_SIZE, "521")
        }
        runAllTests()
    }

    private fun runAllTests() {
        testSetup()
        testEncFile()
        testLog()
        testLogBlockIteration()
    }

    private fun testSetup() {
        val keyPair = uut.generateKeyPair()
        uut.storeEncryptedKeys(password.toCharArray(), keyPair)
    }

    private fun testEncFile() = runBlocking{
        val plainText = "The quick brown fox jumped over the lazy dog"
        val fileExtension = "extension"
        val inputStream = ByteArrayInputStream(plainText.toByteArray(StandardCharsets.UTF_8))
        val outputStream = ByteArrayOutputStream()

        uut.encryptEncStream(inputStream, outputStream, fileExtension)

        val cipherBytes = outputStream.toByteArray()
        val cipherText = outputStream.toString(StandardCharsets.UTF_8.name())

        assertFalse(
            "Cipher text should not contain any of the plain text",
            plainText.split(" ").any { cipherText.contains(it) }
        )
        testDecryptEncFile(cipherBytes, plainText, fileExtension)
    }

    private fun getPrivateKey(): PrivateKey {
        val unlockResult = uut.unlock(password.toCharArray())
        assertTrue("Unlock should be successful", unlockResult is UnlockResult.Success)
        return (unlockResult as UnlockResult.Success).privateKey
    }

    private fun testDecryptEncFile(
        cipherBytes: ByteArray,
        plainText: String,
        fileExtension: String
    ) = runBlocking {
        val inputStream = ByteArrayInputStream(cipherBytes)
        val outputStream = ByteArrayOutputStream()

        val decExtension = uut.decryptEncStream(getPrivateKey(), inputStream, outputStream)
        val decPlainText = outputStream.toString(StandardCharsets.UTF_8.name())
        Assert.assertEquals(
            "Decrypted text should be the same as the plain text",
            plainText,
            decPlainText
        )
        Assert.assertEquals(
            "Decrypted extension should be the same as the plain extension",
            fileExtension,
            decExtension
        )
    }

    private fun testLog() = runBlocking {
        val plainText = "The quick brown fox jumped over the lazy dog"
        val expectedPlainText = plainText + newLine + newLine
        val cipherBytes = encryptNextLog(plainText)
        val inputStream = ByteArrayInputStream(cipherBytes)
        val outputStream = ByteArrayOutputStream()
        val printStream = PrintStream(outputStream, true, StandardCharsets.UTF_8.name())
        val privateKey = getPrivateKey()
        uut.decryptLogStream(privateKey, inputStream, printStream)
        val decPlainText = outputStream.toString(StandardCharsets.UTF_8.name())
        Assert.assertEquals(
            "Decrypted text should be the same as the plain text",
            expectedPlainText,
            decPlainText
        )
    }

    private fun testLogBlockIteration() {
        val plainText1 = "The quick brown fox jumped over the lazy dog"
        val plainText2 = "The smaller red squirrel leaped above the sleepy cat"
        val cipherBytes1 = encryptNextLog(plainText1)
        val cipherBytes2 = encryptNextLog(plainText2)
        val allBytes = cipherBytes1 + cipherBytes2
        ByteArrayInputStream(allBytes).use { inputStream ->
            assertNextLog(inputStream, plainText1, cipherBytes1)
            assertNextLog(inputStream, plainText2, cipherBytes2)
        }
    }

    private fun assertNextLog(
        inputStream: InputStream,
        expectedPlainText: String,
        expectedBytes: ByteArray
    ) {
        val lc = ByteArray(4)
        Assert.assertTrue(uut.logHasNext(inputStream, lc))

        val (logDataBlocks, entryText) =
            uut.getNextLogTextWithDataBlocks(inputStream, getPrivateKey(), lc)

        Assert.assertEquals("Log should have correct plain text", expectedPlainText, entryText)
        Assert.assertTrue(
            "All cipher bytes should be in the ldb",
            expectedBytes.contentEquals(logDataBlocks.asOneBlock)
        )
    }

    private fun encryptNextLog(plainText: String): ByteArray = runBlocking {
        val outputStream = ByteArrayOutputStream()
        uut.encryptLogStream(plainText, outputStream)
        val cipherBytes = outputStream.toByteArray()
        val cipherText = outputStream.toString(StandardCharsets.UTF_8.name())
        assertFalse(
            "Cipher text should not contain any of the plain text",
            plainText.split(" ").any { cipherText.contains(it) }
        )
        return@runBlocking cipherBytes
    }
}