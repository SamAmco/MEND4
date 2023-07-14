package co.samco.mend4.core.crypto.impl

import co.samco.mend4.core.IBase64EncodingProvider
import co.samco.mend4.core.Settings
import co.samco.mend4.core.bean.LogDataBlocks
import co.samco.mend4.core.bean.LogDataBlocksAndText
import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.core.exception.MalformedLogFileException
import co.samco.mend4.core.exception.NoSuchSettingException
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.jce.spec.IESParameterSpec
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidParameterException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class DefaultJCECryptoProvider(
    private val settings: Settings,
    private val encoder: IBase64EncodingProvider
) : CryptoProvider {

    companion object {
        //common byte lengths
        private const val SALT_SIZE = 16
        private const val IV_SIZE = 16

        //Length code size is 4 bytes for 32 bit integers. Changes to this will require
        // changes in the encoding/decoding of length codes.
        private const val LENGTH_CODE_SIZE = 4

        //These used to be dynamic, but it turns out the implementation pretty much
        // relies on these settings. CTR will return a ciphertext with the same length.
        // Key length could be dynamic but 256 is basically the largest broadly supported
        // length anyway.
        private const val SYMMETRIC_CIPHER_NAME = "AES"
        private const val SYMMETRIC_CIPHER_TRANSFORM = "AES/CTR/NoPadding"
        private const val SYMMETRIC_KEY_SIZE = 256
    }

    private val random = SecureRandom()

    private fun generateRandomIv(): IvParameterSpec {
        val iv = ByteArray(IV_SIZE)
        random.nextBytes(iv)
        return IvParameterSpec(iv)
    }

    private fun generateSymmetricKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance(SYMMETRIC_CIPHER_NAME)
        keyGen.init(SYMMETRIC_KEY_SIZE)
        return keyGen.generateKey()
    }

    private fun getSymmetricCipherKeyFromBytes(keyBytes: ByteArray): SecretKey {
        return SecretKeySpec(keyBytes, 0, keyBytes.size, SYMMETRIC_CIPHER_NAME)
    }

    private fun getSymmetricCipherFromPassword(
        password: CharArray,
        mode: Int,
        keyFactorySalt: ByteArray,
        privateKeyCipherIv: IvParameterSpec,
    ): Cipher {
        val argon2 = Argon2BytesGenerator()

        val iterations =
            settings.getValue(Settings.Name.PW_KEY_FACTORY_ITERATIONS)?.toInt()
                ?: throw NoSuchSettingException(Settings.Name.PW_KEY_FACTORY_ITERATIONS)

        val parallelism =
            settings.getValue(Settings.Name.PW_KEY_FACTORY_PARALLELISM)?.toInt()
                ?: throw NoSuchSettingException(Settings.Name.PW_KEY_FACTORY_PARALLELISM)

        val memoryKb =
            settings.getValue(Settings.Name.PW_KEY_FACTORY_MEMORY_KB)?.toInt()
                ?: throw NoSuchSettingException(Settings.Name.PW_KEY_FACTORY_MEMORY_KB)

        argon2.init(
            Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(keyFactorySalt)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .withMemoryAsKB(memoryKb)
                .build()
        )

        val secretBytes = ByteArray(SYMMETRIC_KEY_SIZE / 8)
        argon2.generateBytes(password, secretBytes)

        val symmetricKey: SecretKey = SecretKeySpec(secretBytes, SYMMETRIC_CIPHER_NAME)
        val symmetricCipher = Cipher.getInstance(SYMMETRIC_CIPHER_TRANSFORM)
        symmetricCipher.init(mode, symmetricKey, privateKeyCipherIv)
        return symmetricCipher
    }

    private fun getSymmetricEncryptCipher(key: SecretKey, iv: IvParameterSpec) =
        Cipher.getInstance(SYMMETRIC_CIPHER_TRANSFORM).apply {
            init(Cipher.ENCRYPT_MODE, key, iv)
        }

    private fun getSymmetricDecryptCipher(key: SecretKey, iv: IvParameterSpec): Cipher {
        val cipher = Cipher.getInstance(SYMMETRIC_CIPHER_TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key, iv)
        return cipher
    }

    private fun getAsymmetricCipherParamSpec(cipherTransform: String): AlgorithmParameterSpec? {
        if (!cipherTransform.startsWith("ECIES")
            && !cipherTransform.startsWith("XIES")
        ) return null

        if (cipherTransform != "ECIES" && cipherTransform != "XIES") {
            throw InvalidAlgorithmParameterException(
                "IES with explicit parameters is not yet supported."
            )
        }

        //We are not using a nonce, but the only thing this cipher ever encrypts is unique
        // randomly generated AES keys, so it should be fine.
        return IESParameterSpec(
            /* derivation = */ null,
            /* encoding = */ null,
            /* macKeySize = */ 256,
            /* cipherKeySize = */ 256,
            /* nonce = */ null,
            /* usePointCompression = */ false
        )
    }

    private fun getAsymmetricEncryptCipher(): Cipher {
        val cipherTransform = settings.getValue(Settings.Name.ASYMMETRIC_CIPHER_TRANSFORM)
            ?: throw NoSuchSettingException(Settings.Name.ASYMMETRIC_CIPHER_TRANSFORM)
        val cipher = Cipher.getInstance(cipherTransform)
        val cipherParamSpec = getAsymmetricCipherParamSpec(cipherTransform)
        cipher.init(Cipher.ENCRYPT_MODE, getPublicKey(), cipherParamSpec)
        return cipher
    }

    private fun getAsymmetricDecryptCipher(privateKey: PrivateKey): Cipher {
        val cipherTransform = settings.getValue(Settings.Name.ASYMMETRIC_CIPHER_TRANSFORM)
            ?: throw NoSuchSettingException(Settings.Name.ASYMMETRIC_CIPHER_TRANSFORM)
        val cipher = Cipher.getInstance(cipherTransform)
        val cipherParamSpec = getAsymmetricCipherParamSpec(cipherTransform)
        cipher.init(Cipher.DECRYPT_MODE, privateKey, cipherParamSpec)
        return cipher
    }

    private fun decryptSymmetricKeyFromLdb(ldb: LogDataBlocks, privateKey: PrivateKey): SecretKey {
        val asymmetricCipher = getAsymmetricDecryptCipher(privateKey)
        val symmetricKeyBytes = asymmetricCipher.doFinal(ldb.encKey)
        return getSymmetricCipherKeyFromBytes(symmetricKeyBytes)
    }

    private fun decryptEntryFromLog(
        ldb: LogDataBlocks,
        symmetricKey: SecretKey,
        iv: IvParameterSpec
    ): ByteArray {
        val cipher = getSymmetricDecryptCipher(symmetricKey, iv)
        return cipher.doFinal(ldb.encEntry)
    }

    private fun getIntAsLengthCode(number: Int): ByteArray {
        return ByteBuffer.allocate(LENGTH_CODE_SIZE).putInt(number).array()
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
        val encSymmetricKey = readMendFileBytes(inputStream, getLengthCodeAsInt(lc1Bytes))
        val lc2Bytes = readMendFileBytes(inputStream, LENGTH_CODE_SIZE)
        val iv = readMendFileBytes(inputStream, getLengthCodeAsInt(lc2Bytes))
        val lc3Bytes = readMendFileBytes(inputStream, LENGTH_CODE_SIZE)
        val encEntry = readMendFileBytes(inputStream, getLengthCodeAsInt(lc3Bytes))
        return LogDataBlocks(lc1Bytes, encSymmetricKey, lc2Bytes, iv, lc3Bytes, encEntry)
    }

    override fun getPublicKey(): PublicKey {
        val publicKeyEncoded = settings.getValue(Settings.Name.PUBLIC_KEY)
            ?: throw NoSuchSettingException(Settings.Name.PUBLIC_KEY)
        val asymmetricCipherName = settings.getValue(Settings.Name.ASYMMETRIC_CIPHER_NAME)
            ?: throw NoSuchSettingException(Settings.Name.ASYMMETRIC_CIPHER_NAME)

        val keyBytes = encoder.decodeBase64(publicKeyEncoded)
        return KeyFactory.getInstance(asymmetricCipherName)
            .generatePublic(X509EncodedKeySpec(keyBytes)) as PublicKey
    }

    override fun encryptEncStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        fileExtension: String
    ) {
        //Generate a new symmetric key and encrypt it with the public key
        val symmetricKey = generateSymmetricKey()
        val encryptedSymmetricKey = getAsymmetricEncryptCipher().doFinal(symmetricKey.encoded)
        val lengthCode1 = getIntAsLengthCode(encryptedSymmetricKey.size)

        //Generate a random iv
        val iv = generateRandomIv()
        val lengthCode2 = getIntAsLengthCode(iv.iv.size)

        //Create the symmetric cipher
        val symmetricCipher = getSymmetricEncryptCipher(symmetricKey, iv)

        //Encrypt the file extension
        val encFileExtensionBytes = symmetricCipher.doFinal(fileExtension.toByteArray())
        val lengthCode3 = getIntAsLengthCode(encFileExtensionBytes.size)

        //Write the encrypted key and iv in plaintext
        outputStream.write(lengthCode1)
        outputStream.write(encryptedSymmetricKey)
        outputStream.write(lengthCode2)
        outputStream.write(iv.iv)

        //Write the file extension encrypted
        outputStream.write(lengthCode3)
        outputStream.write(encFileExtensionBytes)

        //Write the encrypted file
        val cipherOutputStream = CipherOutputStream(outputStream, symmetricCipher)
        writeToCipherStream(inputStream, cipherOutputStream)
    }

    override fun decryptEncStream(
        privateKey: PrivateKey,
        inputStream: InputStream,
        outputStream: OutputStream
    ): String {
        //Read the length of the encrypted symmetric key
        val asymmetricCipher = getAsymmetricDecryptCipher(privateKey)
        val lengthCode1 = readMendFileBytes(inputStream, LENGTH_CODE_SIZE)

        //Read and decrypt the encrypted symmetric key
        val encSymmetricCipherKey = readMendFileBytes(inputStream, getLengthCodeAsInt(lengthCode1))
        val symmetricKeyBytes = asymmetricCipher.doFinal(encSymmetricCipherKey)
        val symmetricCipherKey = getSymmetricCipherKeyFromBytes(symmetricKeyBytes)

        //Read the iv
        val lengthCode2 = readMendFileBytes(inputStream, LENGTH_CODE_SIZE)
        val ivBytes = readMendFileBytes(inputStream, getLengthCodeAsInt(lengthCode2))
        val iv = IvParameterSpec(ivBytes)

        //Get the decrypt cipher
        val decryptCipher = getSymmetricDecryptCipher(symmetricCipherKey, iv)
        val cipherOutputStream = CipherOutputStream(outputStream, decryptCipher)

        //Read and decrypt the file extension
        val lengthCode3 = readMendFileBytes(inputStream, LENGTH_CODE_SIZE)
        val encFileExtension = readMendFileBytes(inputStream, getLengthCodeAsInt(lengthCode3))
        val fileExtension = String(decryptCipher.doFinal(encFileExtension))

        //Decrypt the file contents
        writeToCipherStream(inputStream, cipherOutputStream)

        //Return the file extension
        return fileExtension
    }

    override fun getNextLogTextWithDataBlocks(
        inputStream: InputStream,
        privateKey: PrivateKey,
        lc1Bytes: ByteArray
    ): LogDataBlocksAndText {
        val ldb = getNextLogBytes(inputStream, lc1Bytes)
        val symmetricKey = decryptSymmetricKeyFromLdb(ldb, privateKey)
        val iv = IvParameterSpec(ldb.iv)
        val entry = decryptEntryFromLog(ldb, symmetricKey, iv)
        return LogDataBlocksAndText(ldb, String(entry, StandardCharsets.UTF_8))
    }

    override fun getPrivateKeyFromBytes(privateKey: ByteArray): PrivateKey {
        val asymmetricCipherName = settings.getValue(Settings.Name.ASYMMETRIC_CIPHER_NAME)
            ?: throw NoSuchSettingException(Settings.Name.ASYMMETRIC_CIPHER_NAME)

        return KeyFactory.getInstance(asymmetricCipherName)
            .generatePrivate(PKCS8EncodedKeySpec(privateKey))
    }

    override fun decryptLogStream(
        privateKey: PrivateKey,
        inputStream: InputStream,
        outputStream: PrintStream
    ) {
        val lc1Bytes = ByteArray(LENGTH_CODE_SIZE)
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

    override fun encryptLogStream(logText: String, outputStream: OutputStream) {
        //Generate a new symmetric key
        val symmetricKey = generateSymmetricKey()

        //Create a new symmetric cipher
        val iv = generateRandomIv()
        val symmetricCipher = getSymmetricEncryptCipher(symmetricKey, iv)

        //Encrypt the symmetric key with the public key
        val asymmetricCipher = getAsymmetricEncryptCipher()
        val encryptedSymmetricKey = asymmetricCipher.doFinal(symmetricKey.encoded)

        //Encrypt the log text
        val cipherText = symmetricCipher.doFinal(logText.toByteArray(StandardCharsets.UTF_8))

        //Calculate the length codes
        val lengthCode1 = ByteBuffer
            .allocate(LENGTH_CODE_SIZE)
            .putInt(encryptedSymmetricKey.size)
            .array()
        val lengthCode2 = ByteBuffer
            .allocate(LENGTH_CODE_SIZE)
            .putInt(iv.iv.size)
            .array()
        val lengthCode3 = ByteBuffer
            .allocate(LENGTH_CODE_SIZE)
            .putInt(cipherText.size)
            .array()

        //Allocate the output buffer
        val output = ByteBuffer.allocate(
            lengthCode1.size
                    + encryptedSymmetricKey.size
                    + lengthCode2.size
                    + iv.iv.size
                    + lengthCode3.size
                    + cipherText.size
        )

        //Write the encrypted key to the buffer
        output.put(lengthCode1)
        output.put(encryptedSymmetricKey)

        //Write the iv to the buffer
        output.put(lengthCode2)
        output.put(iv.iv)

        //Write the encrypted log text to the buffer
        output.put(lengthCode3)
        output.put(cipherText)

        //Write the whole buffer to the output stream. We don't want to fail in the middle of
        //writing the log file.
        outputStream.write(output.array())
    }

    override fun logHasNext(logInputStream: InputStream, lc1Bytes: ByteArray): Boolean {
        if (lc1Bytes.size != LENGTH_CODE_SIZE) throw InvalidAlgorithmParameterException(
            "Expected lc1Bytes to be initialized and have length "
                    + LENGTH_CODE_SIZE
        )
        return logInputStream.read(lc1Bytes) == LENGTH_CODE_SIZE
    }

    override fun generateKeyPair(): KeyPair {
        val asymmetricCipherName = settings.getValue(Settings.Name.ASYMMETRIC_CIPHER_NAME)
            ?: throw NoSuchSettingException(Settings.Name.ASYMMETRIC_CIPHER_NAME)
        val asymmetricKeySize = settings.getValue(Settings.Name.ASYMMETRIC_KEY_SIZE)?.toInt()
            ?: throw NoSuchSettingException(Settings.Name.ASYMMETRIC_KEY_SIZE)

        val keyGen = KeyPairGenerator.getInstance(asymmetricCipherName)
        keyGen.initialize(asymmetricKeySize)
        return keyGen.genKeyPair()
    }

    override fun getKeyPairFromBytes(privateKey: ByteArray, publicKey: ByteArray): KeyPair {
        val asymmetricCipherName = settings.getValue(Settings.Name.ASYMMETRIC_CIPHER_NAME)
            ?: throw NoSuchSettingException(Settings.Name.ASYMMETRIC_CIPHER_NAME)

        val kf = KeyFactory.getInstance(asymmetricCipherName)
        return KeyPair(
            kf.generatePublic(X509EncodedKeySpec(publicKey)),
            kf.generatePrivate(PKCS8EncodedKeySpec(privateKey))
        )
    }

    override fun storeEncryptedKeys(password: CharArray, keyPair: KeyPair) {
        if (keyPair.public.format != "X.509") {
            throw InvalidParameterException("Public key must be in X.509 format")
        }

        val keyFactorySalt = ByteArray(SALT_SIZE)
        random.nextBytes(keyFactorySalt)
        val privateKeyCipherIv = generateRandomIv()

        val cipher = getSymmetricCipherFromPassword(
            password,
            Cipher.ENCRYPT_MODE,
            keyFactorySalt,
            privateKeyCipherIv
        )
        val encryptedPrivateKey = cipher.doFinal(keyPair.private.encoded)

        settings.setValue(
            Settings.Name.PUBLIC_KEY,
            encoder.encodeBase64URLSafeString(keyPair.public.encoded)
        )
        settings.setValue(
            Settings.Name.PW_KEY_FACTORY_SALT,
            encoder.encodeBase64URLSafeString(keyFactorySalt)
        )
        settings.setValue(
            Settings.Name.PW_PRIVATE_KEY_CIPHER_IV,
            encoder.encodeBase64URLSafeString(privateKeyCipherIv.iv)
        )
        settings.setValue(
            Settings.Name.ENCRYPTED_PRIVATE_KEY,
            encoder.encodeBase64URLSafeString(encryptedPrivateKey)
        )
    }

    override fun checkPassword(password: CharArray): Boolean {
        //Generate some random set of bytes
        val passCheck = ByteArray(20)
        random.nextBytes(passCheck)

        //encrypt them with the public key
        val asymmetricCipher = getAsymmetricEncryptCipher()
        val cipherTextBytes = asymmetricCipher.doFinal(passCheck)
        val plainText: ByteArray = try {
            //try to then decrypt the private key with the password
            val privateKeyBytes = decryptEncodedPrivateKey(password)
            val privateKey = getPrivateKeyFromBytes(privateKeyBytes)
            //use the decrypted private key to decrypt the encrypted random bytes
            val decAsymmetricCipher = getAsymmetricDecryptCipher(privateKey)
            decAsymmetricCipher.doFinal(cipherTextBytes)
        } catch (t: Throwable) {
            return false
        }

        //assert that the two sets of bytes are the same
        return plainText.contentEquals(passCheck)
    }

    override fun decryptEncodedPrivateKey(password: CharArray): ByteArray {
        val keyFactorySaltEncoded = settings.getValue(Settings.Name.PW_KEY_FACTORY_SALT)
            ?: throw NoSuchSettingException(Settings.Name.PW_KEY_FACTORY_SALT)
        val privateKeyCipherIvEncoded = settings.getValue(Settings.Name.PW_PRIVATE_KEY_CIPHER_IV)
            ?: throw NoSuchSettingException(Settings.Name.PW_PRIVATE_KEY_CIPHER_IV)
        val encryptedPrivateKeyEncoded = settings.getValue(Settings.Name.ENCRYPTED_PRIVATE_KEY)
            ?: throw NoSuchSettingException(Settings.Name.ENCRYPTED_PRIVATE_KEY)

        val keyFactorySalt = encoder.decodeBase64(keyFactorySaltEncoded)
        val privateKeyCipherIv = IvParameterSpec(encoder.decodeBase64(privateKeyCipherIvEncoded))

        return getSymmetricCipherFromPassword(
            password = password,
            mode = Cipher.DECRYPT_MODE,
            keyFactorySalt = keyFactorySalt,
            privateKeyCipherIv = privateKeyCipherIv
        ).doFinal(
            encoder.decodeBase64(encryptedPrivateKeyEncoded)
        )
    }
}