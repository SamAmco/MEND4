package co.samco.mend4.core.crypto.impl

import co.samco.mend4.core.IBase64EncodingProvider
import co.samco.mend4.core.bean.EncodedKeyInfo
import co.samco.mend4.core.bean.LogDataBlocks
import co.samco.mend4.core.bean.LogDataBlocksAndText
import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.core.exception.MalformedLogFileException
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.InvalidAlgorithmParameterException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.KeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Random
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class DefaultJCECryptoProvider(
    private val iv: IvParameterSpec,
    private val aesKeySize: Int,
    private val rsaKeySize: Int,
    private val aesAlgorithm: String,
    private val rsaAlgorithm: String,
    private val passcheckSalt: ByteArray,
    private val aesKeyGenIterations: Int,
    private val encoder: IBase64EncodingProvider
) : CryptoProvider {
    private fun generateAesKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(aesKeySize)
        return keyGen.generateKey()
    }

    private fun getAesKeyFromBytes(aesKeyBytes: ByteArray): SecretKey {
        return SecretKeySpec(aesKeyBytes, 0, aesKeyBytes.size, "AES")
    }

    private fun getAesCipherFromPassword(password: CharArray, mode: Int): Cipher {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val spec: KeySpec = PBEKeySpec(password, passcheckSalt, aesKeyGenIterations, aesKeySize)
        val tmp = factory.generateSecret(spec)
        val aesKey: SecretKey = SecretKeySpec(tmp.encoded, "AES")
        val aesCipher = Cipher.getInstance(aesAlgorithm)
        aesCipher.init(mode, aesKey, iv)
        return aesCipher
    }

    private fun getAesEncryptCipher(aesKey: SecretKey): Cipher {
        val aesCipher = Cipher.getInstance(aesAlgorithm)
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, iv)
        return aesCipher
    }

    private fun getAesDecryptCipher(aesKey: SecretKey): Cipher {
        val aesCipher = Cipher.getInstance(aesAlgorithm)
        aesCipher.init(Cipher.DECRYPT_MODE, aesKey, iv)
        return aesCipher
    }

    private fun getRsaEncrytpCipher(publicKey: RSAPublicKey): Cipher {
        val rsaCipher = Cipher.getInstance(rsaAlgorithm)
        rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return rsaCipher
    }

    private fun getRsaDecryptCipher(privateKey: RSAPrivateKey): Cipher {
        val rsaCipher = Cipher.getInstance(rsaAlgorithm)
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey)
        return rsaCipher
    }

    private fun decryptAesKeyFromLog(ldb: LogDataBlocks, privateKey: RSAPrivateKey): SecretKey {
        val rsaCipher = getRsaDecryptCipher(privateKey)
        val aesKeyBytes = rsaCipher.doFinal(ldb.encAesKey)
        return getAesKeyFromBytes(aesKeyBytes)
    }

    private fun decryptEntryFromLog(ldb: LogDataBlocks, aesKey: SecretKey): ByteArray {
        val aesCipher = getAesDecryptCipher(aesKey)
        return aesCipher.doFinal(ldb.encEntry)
    }

    private fun getIntAsLengthCode(number: Int): ByteArray {
        return ByteBuffer.allocate(CryptoProvider.LENGTH_CODE_SIZE).putInt(number).array()
    }

    private fun getLengthCodeAsInt(bytes: ByteArray): Int {
        //big-endian by default
        return ByteBuffer.wrap(bytes).int
    }

    private fun writeToCipherStream(
        inputStream: InputStream,
        cipherOutputStream: CipherOutputStream
    ) {
        val buffer = ByteArray(8192)
        var count: Int
        while (inputStream.read(buffer).also { count = it } > 0) {
            cipherOutputStream.write(buffer, 0, count)
        }
    }

    private fun readMendFileBytes(inputStream: InputStream, number: Int): ByteArray {
        val bytes = ByteArray(number)
        if (inputStream.read(bytes) != number) throw MalformedLogFileException()
        return bytes
    }

    private fun getNextLogBytes(inputStream: InputStream, lc1Bytes: ByteArray): LogDataBlocks {
        val encAesKey = readMendFileBytes(inputStream, getLengthCodeAsInt(lc1Bytes))
        val lc2Bytes = readMendFileBytes(inputStream, CryptoProvider.LENGTH_CODE_SIZE)
        val encEntry = readMendFileBytes(inputStream, getLengthCodeAsInt(lc2Bytes))
        return LogDataBlocks(lc1Bytes, encAesKey, lc2Bytes, encEntry)
    }

    override fun encryptEncStream(
        publicKey: RSAPublicKey,
        inputStream: InputStream,
        outputStream: OutputStream,
        fileExtension: String
    ) {
        val aesKey = generateAesKey()
        val rsaCipher = getRsaEncrytpCipher(publicKey)
        val encryptedAesKey = rsaCipher.doFinal(aesKey.encoded)
        val lengthCode1 = getIntAsLengthCode(encryptedAesKey.size)
        val fileExtensionBytes = rsaCipher.doFinal(fileExtension.toByteArray())
        outputStream.write(lengthCode1)
        outputStream.write(encryptedAesKey)
        outputStream.write(getIntAsLengthCode(fileExtensionBytes.size))
        outputStream.write(fileExtensionBytes)
        writeToCipherStream(
            inputStream,
            CipherOutputStream(outputStream, getAesEncryptCipher(aesKey))
        )
    }

    override fun decryptEncStream(
        privateKey: RSAPrivateKey,
        inputStream: InputStream,
        outputStream: OutputStream
    ): String {
        val rsaCipher = getRsaDecryptCipher(privateKey)
        val encAesKey = readMendFileBytes(
            inputStream,
            getLengthCodeAsInt(readMendFileBytes(inputStream, CryptoProvider.LENGTH_CODE_SIZE))
        )
        val aesKeyBytes = rsaCipher.doFinal(encAesKey)
        val aesKey = getAesKeyFromBytes(aesKeyBytes)
        val encFileExtension = readMendFileBytes(
            inputStream,
            getLengthCodeAsInt(readMendFileBytes(inputStream, CryptoProvider.LENGTH_CODE_SIZE))
        )
        val fileExtension = String(rsaCipher.doFinal(encFileExtension))
        writeToCipherStream(
            inputStream,
            CipherOutputStream(outputStream, getAesDecryptCipher(aesKey))
        )
        return fileExtension
    }

    override fun getNextLogTextWithDataBlocks(
        inputStream: InputStream,
        privateKey: RSAPrivateKey,
        lc1Bytes: ByteArray
    ): LogDataBlocksAndText {
        val ldb = getNextLogBytes(inputStream, lc1Bytes)
        val aesKey = decryptAesKeyFromLog(ldb, privateKey)
        val entry = decryptEntryFromLog(ldb, aesKey)
        return LogDataBlocksAndText(ldb, String(entry, StandardCharsets.UTF_8))
    }

    override fun getPublicKeyFromBytes(publicKey: ByteArray): RSAPublicKey {
        return KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(publicKey)) as RSAPublicKey
    }

    override fun getPrivateKeyFromBytes(privateKey: ByteArray): RSAPrivateKey {
        return KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(privateKey)) as RSAPrivateKey
    }

    override fun decryptLogStream(
        privateKey: RSAPrivateKey,
        inputStream: InputStream,
        outputStream: PrintStream
    ) {
        val lc1Bytes = ByteArray(CryptoProvider.LENGTH_CODE_SIZE)
        while (logHasNext(inputStream, lc1Bytes)) {
            outputStream.println(
                getNextLogTextWithDataBlocks(
                    inputStream,
                    privateKey,
                    lc1Bytes
                ).entryText
            )
            outputStream.println()
        }
    }

    override fun encryptLogStream(
        publicRsaKey: RSAPublicKey,
        text: String,
        fos: OutputStream
    ) {
        val aesKey = generateAesKey()
        val aesCipher = getAesEncryptCipher(aesKey)
        val rsaCipher = getRsaEncrytpCipher(publicRsaKey)
        val cipherText = aesCipher.doFinal(text.toByteArray(StandardCharsets.UTF_8))
        val encryptedAesKey = rsaCipher.doFinal(aesKey.encoded)
        val lengthCode1 =
            ByteBuffer.allocate(CryptoProvider.LENGTH_CODE_SIZE).putInt(encryptedAesKey.size)
                .array()
        val lengthCode2 =
            ByteBuffer.allocate(CryptoProvider.LENGTH_CODE_SIZE).putInt(cipherText.size).array()
        val output = ByteBuffer.allocate(
            lengthCode1.size
                    + encryptedAesKey.size
                    + lengthCode2.size
                    + cipherText.size
        )
        output.put(lengthCode1)
        output.put(encryptedAesKey)
        output.put(lengthCode2)
        output.put(cipherText)
        fos.write(output.array())
    }

    override fun logHasNext(logInputStream: InputStream, lc1Bytes: ByteArray): Boolean {
        if (lc1Bytes.size != CryptoProvider.LENGTH_CODE_SIZE) throw InvalidAlgorithmParameterException(
            "Expected lc1Bytes to be initialized and have length "
                    + CryptoProvider.LENGTH_CODE_SIZE
        )
        return logInputStream.read(lc1Bytes) == CryptoProvider.LENGTH_CODE_SIZE
    }

    override fun generateKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(rsaKeySize)
        return keyGen.genKeyPair()
    }

    override fun getKeyPairFromBytes(privateKey: ByteArray, publicKey: ByteArray): KeyPair {
        val kf = KeyFactory.getInstance("RSA")
        val rsaPrivateKey = kf.generatePrivate(PKCS8EncodedKeySpec(privateKey)) as RSAPrivateKey
        val rsaPublicKey = kf.generatePublic(X509EncodedKeySpec(publicKey)) as RSAPublicKey
        return KeyPair(rsaPublicKey, rsaPrivateKey)
    }

    override fun getEncodedKeyInfo(password: CharArray, keyPair: KeyPair): EncodedKeyInfo {
        val aesCipher = getAesCipherFromPassword(password, Cipher.ENCRYPT_MODE)
        val encryptedPrivateKey = aesCipher.doFinal(keyPair.private.encoded)
        return EncodedKeyInfo(
            encoder.encodeBase64URLSafeString(encryptedPrivateKey),
            encoder.encodeBase64URLSafeString(keyPair.public.encoded),
            (keyPair.public as RSAPublicKey).modulus.bitLength()
        )
    }

    override fun checkPassword(
        password: CharArray,
        encodedPrivateKey: String,
        publicKey: RSAPublicKey
    ): Boolean {
        //Generate some random set of bytes
        val passCheck = ByteArray(20)
        Random().nextBytes(passCheck)

        //encrypt them with the public key
        val encRsaCipher = getRsaEncrytpCipher(publicKey)
        val cipherTextBytes = encRsaCipher.doFinal(passCheck)
        val plainText: ByteArray = try {
            //try to then decrypt the private key with the password
            val privateKeyBytes = decryptEncodedKey(password, encodedPrivateKey)
            val privateKey = getPrivateKeyFromBytes(privateKeyBytes)
            //use the decrypted private key to decrypt the encrypted random bytes
            val decRsaCipher = getRsaDecryptCipher(privateKey)
            decRsaCipher.doFinal(cipherTextBytes)
        } catch (t: Throwable) {
            return false
        }

        //assert that the two sets of bytes are the same
        return plainText.contentEquals(passCheck)
    }

    override fun decryptEncodedKey(password: CharArray, encodedKey: String): ByteArray {
        return getAesCipherFromPassword(password, Cipher.DECRYPT_MODE).doFinal(
            encoder.decodeBase64(
                encodedKey
            )
        )
    }
}