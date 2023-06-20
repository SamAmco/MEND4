package co.samco.mend4.core.crypto

import co.samco.mend4.core.bean.EncodedKeyInfo
import co.samco.mend4.core.bean.LogDataBlocksAndText
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintStream
import java.security.KeyPair
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

interface CryptoProvider {
    fun encryptEncStream(
        publicKey: RSAPublicKey,
        inputStream: InputStream,
        outputStream: OutputStream,
        fileExtension: String
    )

    fun decryptEncStream(
        privateKey: RSAPrivateKey,
        inputStream: InputStream,
        outputStream: OutputStream
    ): String

    fun encryptLogStream(publicRsaKey: RSAPublicKey, text: String, fos: OutputStream)

    fun decryptLogStream(
        privateKey: RSAPrivateKey,
        inputStream: InputStream,
        outputStream: PrintStream
    )

    fun logHasNext(logInputStream: InputStream, lc1Bytes: ByteArray): Boolean

    fun getNextLogTextWithDataBlocks(
        inputStream: InputStream,
        privateKey: RSAPrivateKey,
        lc1Bytes: ByteArray
    ): LogDataBlocksAndText

    fun getPublicKeyFromBytes(publicKey: ByteArray): RSAPublicKey

    fun getPrivateKeyFromBytes(privateKey: ByteArray): RSAPrivateKey

    fun generateKeyPair(): KeyPair

    fun getKeyPairFromBytes(privateKey: ByteArray, publicKey: ByteArray): KeyPair

    fun checkPassword(
        password: CharArray,
        encodedPrivateKey: String,
        publicKey: RSAPublicKey
    ): Boolean

    fun decryptEncodedKey(password: CharArray, encodedKey: String): ByteArray

    fun getEncodedKeyInfo(password: CharArray, keyPair: KeyPair): EncodedKeyInfo

    companion object {
        const val LENGTH_CODE_SIZE = 4
    }
}