package crypto

import co.samco.mend4.core.AppProperties
import co.samco.mend4.core.IBase64EncodingProvider
import co.samco.mend4.core.Settings
import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.core.crypto.impl.DefaultJCECryptoProvider
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64

class DefaultJCECryptoProviderTest {
    private val newLine = System.getProperty("line.separator")

    private var rsaKeyPair: KeyPair? = null

    private val settings = mock<Settings>()
    private val encodingProvider = object : IBase64EncodingProvider {
        override fun decodeBase64(base64String: String): ByteArray {
            return Base64.getDecoder().decode(base64String)
        }

        override fun encodeBase64URLSafeString(data: ByteArray): String {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(data)
        }

    }

    private val uut = DefaultJCECryptoProvider(settings, encodingProvider)

    @Before
    fun setup() {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(AppProperties.PREFERRED_ASYMMETRIC_KEY_SIZE)
        rsaKeyPair = keyGen.genKeyPair()
    }

    @Test
    fun encTest() {
        val plainText = "The quick brown fox jumped over the lazy dog"
        val fileExtension = "extension"
        var cipherBytes: ByteArray
        var cipherText: String
        ByteArrayInputStream(plainText.toByteArray(StandardCharsets.UTF_8)).use { inputStream ->
            ByteArrayOutputStream().use { outputStream ->
                uut.encryptEncStream(
                    rsaKeyPair!!.public as RSAPublicKey,
                    inputStream,
                    outputStream,
                    fileExtension
                )
                cipherBytes = outputStream.toByteArray()
                cipherText = outputStream.toString(StandardCharsets.UTF_8.name())
                for (s in plainText.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()) {
                    Assert.assertTrue(
                        "Cipher text should not contain any of the plain text",
                        !cipherText.contains(
                            s
                        )
                    )
                }
            }
        }
        ByteArrayInputStream(cipherBytes).use { inputStream ->
            ByteArrayOutputStream().use { outputStream ->
                val decExtension = uut.decryptEncStream(
                    (rsaKeyPair!!.private as RSAPrivateKey),
                    inputStream,
                    outputStream
                )
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
        }
    }

    @Test
    fun logTest() {
        val plainText = "The quick brown fox jumped over the lazy dog"
        val expectedPlainText = plainText + newLine + newLine
        val cipherBytes = encryptNextLog(plainText)
        ByteArrayInputStream(cipherBytes).use { inputStream ->
            ByteArrayOutputStream().use { outputStream ->
                PrintStream(outputStream, true, StandardCharsets.UTF_8.name()).use { printStream ->
                    uut.decryptLogStream(
                        (rsaKeyPair!!.private as RSAPrivateKey),
                        inputStream,
                        printStream
                    )
                    val decPlainText = outputStream.toString(StandardCharsets.UTF_8.name())
                    Assert.assertEquals(
                        "Decrypted text should be the same as the plain text",
                        expectedPlainText,
                        decPlainText
                    )
                }
            }
        }
    }

    val nextLogBlockTest: Unit
        get() {
            val plainText1 = "The quick brown fox jumped over the lazy dog"
            val plainText2 = "The smaller red squirrel leaped above the sleepy cat"
            val cipherBytes1 = encryptNextLog(plainText1)
            val cipherBytes2 = encryptNextLog(plainText2)
            val allBytes = ArrayUtils.addAll(cipherBytes1, *cipherBytes2)
            ByteArrayInputStream(allBytes).use { inputStream ->
                assertNextLog(inputStream, plainText1, cipherBytes1)
                assertNextLog(inputStream, plainText2, cipherBytes2)
            }
        }

    val encodedKeyInfoCorrectSizeTest: Unit
        get() {
            val pass = "hi".toCharArray()
            val keyInfo: EncodedKeyInfo = uut.storeEncryptedKeys(pass, rsaKeyPair!!)
            Assert.assertEquals(AppProperties.PREFERRED_ASYMMETRIC_KEY_SIZE, keyInfo.getKeySize())
        }

    private fun assertNextLog(
        inputStream: InputStream,
        expectedPlainText: String,
        expectedBytes: ByteArray
    ) {
        val lc = ByteArray(CryptoProvider.LENGTH_CODE_SIZE)
        Assert.assertTrue(uut.logHasNext(inputStream, lc))
        val (logDataBlocks, entryText) = uut.getNextLogTextWithDataBlocks(
            inputStream,
            (rsaKeyPair!!.private as RSAPrivateKey), lc
        )
        Assert.assertEquals("Log should have correct plain text", expectedPlainText, entryText)
        Assert.assertThat(
            "All cipher bytes should be in the ldb", expectedBytes,
            CoreMatchers.equalTo(logDataBlocks.asOneBlock)
        )
    }

    private fun encryptNextLog(plainText: String): ByteArray {
        var cipherBytes: ByteArray
        ByteArrayOutputStream().use { outputStream ->
            uut.encryptLogStream(
                rsaKeyPair!!.public as RSAPublicKey,
                plainText,
                outputStream
            )
            cipherBytes = outputStream.toByteArray()
            val cipherText = outputStream.toString(StandardCharsets.UTF_8.name())
            for (s in plainText.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()) {
                Assert.assertTrue(
                    "Cipher text should not contain any of the plain text", !cipherText.contains(
                        s
                    )
                )
            }
        }
        return cipherBytes
    }
}