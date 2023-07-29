package crypto

import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.IESParameterSpec
import org.junit.Before
import org.junit.Test
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Security
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher

/**
 * These tests are here to assert some basic assumptions about the default cipher parameters of MEND.
 */
class CipherParameterTests {

    private val encodingProvider = TestHelpers.getEncoderImplementation()

    private val asymmetricCipherName = "X448"
    private val asymmetricCipherTransform = "XIESWithSha256"

    private fun getDefaultParams() = IESParameterSpec(
        /* derivation = */ null,
        /* encoding = */ null,
        /* macKeySize = */ 128,
        /* cipherKeySize = */ 256,
        /* nonce = */ null,
        /* usePointCompression = */ false
    )

    @Before
    fun before() {
        Security.addProvider(BouncyCastleProvider())
    }

    @Test
    fun `Two ciphertexts for the same plaintext should be unique`() {
        val keyGen = KeyPairGenerator.getInstance(asymmetricCipherName)
        keyGen.initialize(448)
        val keyPair = keyGen.genKeyPair()

        val cipher = Cipher.getInstance(asymmetricCipherTransform)

        cipher.init(Cipher.ENCRYPT_MODE, keyPair.public, getDefaultParams())

        val cipherText1 = cipher.doFinal("Hello World".toByteArray())
        val cipherText2 = cipher.doFinal("Hello World".toByteArray())

        assertFalse(cipherText1.contentEquals(cipherText2))
    }

    /**
     * Whenever you update the bouncy castle dependency there's a chance that the cipher implementation
     * has been updated and old keys or ciphertexts will be invalid. This test is here to make sure that
     * we don't accidentally break backwards compatibility. If it fails you either need to change
     * the cipher parameters to get the old behaviour or the new version if fundamentally incompatible
     * and it's a new major release requiring re-encrypting all previous data.
     */
    @Test
    fun `Bouncy castle keys and ciphers are backwards compatible`() {
        // For reference this is the public key used to encrypt the cipher text below
        //val public =
        //    "MEIwBQYDK2VvAzkAOurqFbapsW0lWDGjFDTe6_5sWoBhGIEKLn9KLOjDqb_eoa3ox0OcZukij-xrmBr2pMe_u0aNNu4"
        //val pubKeySpec = X509EncodedKeySpec(encodingProvider.decodeBase64(public))
        //val pubKey = KeyFactory.getInstance("X448").generatePublic(pubKeySpec) as PublicKey

        val private =
            "MEYCAQAwBQYDK2VvBDoEOLai7ao8lLAibX_KT0wFFUtGwbGMVMFLYaOxl5p6fo0ZZ7otIvrkVgx7UZ8PmByacLpEU4QRDG5T"

        val privKeyBytes = encodingProvider.decodeBase64(private)
        val privKey = KeyFactory.getInstance(asymmetricCipherName)
            .generatePrivate(PKCS8EncodedKeySpec(privKeyBytes))

        val plainText = "The quick brown fox jumps over the lazy dog.".repeat(10)

        val cipherText = "ntg5HPnMC6ppUZAudtYn3fEuHcfC1_8w5xOSFjEi78eJMylXb5mzlndOHy6bLRW3RhcWtznT4CSeIITjFmwvVMSS7pLQMe4_CoPSdFVQTc-aaafw3rc7ZDBGLrNPVllcj6I2uyVjuVfRzg86ng1x67NZ_ITxV4sFEY7JAXecgNWgScEBXzvmVGc7MRxaE5r_UtTlFMHFi5gHSw-pJDfgDa8vGYcGmCx1Wm5iDyVagsyvzQOUMqDQzPv5e8_i2dBK1iTeeH-EfhXkenaR4EFzoYOXNvKbOKdrs6xOsT7i_hShMyAXpd-XsBBNhu0cVOeSDYSNdQdiaBr_zKdhoKyYboYAjAFYpD6a3ukp-IkJwfvBaeIoarEp9JwYwApXkXxlIvsrDhEzgtztgvFm81mYMwl4an1Wy7xePrjaJH2R4b6dPNm-JqphXfJwoaxNdFZdCiLCv54T_g9o714QMkWM154DKZBpJQjoXSGvwnqYC-pByrfSGc07VmRGszaXEzBJbCoum3ssMXZJMAjqo_KZZTUqR-Nu_Q0jP1nNt2QQxRDmARDt5ssL2fVLVuGj8ETGAq6KyXZieVbFudTs764WHILflLESDc-wuKESH2_puiWwrofRmxMko7McH6btWAA-cKFoI0d-kKarS3_fGJSq5BnzyoAgk807ghheY7yH1KSRrU4PAIJXANhJR3w9gbs7"

        val cipher = Cipher.getInstance(asymmetricCipherTransform)
        cipher.init(Cipher.DECRYPT_MODE, privKey, getDefaultParams())
        val decryptedText = cipher.doFinal(encodingProvider.decodeBase64(cipherText))

        assertEquals(plainText, String(decryptedText))
    }

}