package co.samco.mend4.core.crypto

import co.samco.mend4.core.bean.LogDataBlocksAndText
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintStream
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey

interface CryptoProvider {
    /**
     * Encrypts the input stream and file extension and writes the encrypted data to the output stream.
     */
    fun encryptEncStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        fileExtension: String
    )

    /**
     * Decrypts the input stream and writes the decrypted data to the output stream.
     * @return the file extension of the decrypted file.
     */
    fun decryptEncStream(
        privateKey: PrivateKey,
        inputStream: InputStream,
        outputStream: OutputStream
    ): String

    /**
     * Encrypts the text and writes the encrypted data to the output stream as a log entry.
     */
    fun encryptLogStream(
        logText: String,
        outputStream: OutputStream
    )

    /**
     * Decrypts the input stream as a log and writes the decrypted data to the output stream.
     */
    fun decryptLogStream(
        privateKey: PrivateKey,
        inputStream: InputStream,
        outputStream: PrintStream
    )

    /**
     * Reads 4 bytes from the input stream and puts them into [lc1Bytes].
     * @return true if the 4 bytes were successfully read, false otherwise.
     */
    fun logHasNext(logInputStream: InputStream, lc1Bytes: ByteArray): Boolean

    /**
     * Reads the next log entry from the input stream and decrypts it using the private key.
     * @return the decrypted log text with the full encrypted log bytes.
     */
    fun getNextLogTextWithDataBlocks(
        inputStream: InputStream,
        privateKey: PrivateKey,
        lc1Bytes: ByteArray
    ): LogDataBlocksAndText

    /**
     * Returns a [PrivateKey] from the given byte array. The byte array is expected to be PKCS8 encoded
     */
    fun getPrivateKeyFromBytes(privateKey: ByteArray): PrivateKey

    /**
     * Returns the public key
     */
    fun getPublicKey(): PublicKey

    /**
     * Creates a new [KeyPair].
     */
    fun generateKeyPair(): KeyPair

    /**
     * Returns a [KeyPair] from the given byte arrays. the [privateKey] should be PKCS8 encoded.
     * The [publicKey] should be X509 encoded.
     */
    fun getKeyPairFromBytes(privateKey: ByteArray, publicKey: ByteArray): KeyPair

    /**
     * @return true if the given password is correct, false otherwise.
     */
    fun checkPassword(password: CharArray): Boolean

    /**
     * Decrypts the stored private key using the given password and returns a PKCS8 encoded key
     * as a ByteArray
     */
    fun decryptEncodedPrivateKey(password: CharArray): ByteArray

    /**
     * Encrypts the given private key using the given password and store the key pair.
     */
    fun storeEncryptedKeys(password: CharArray, keyPair: KeyPair)
}