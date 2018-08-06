package co.samco.mend4.core.crypto;

import co.samco.mend4.core.bean.EncodedKeyInfo;
import co.samco.mend4.core.bean.LogDataBlocksAndText;
import co.samco.mend4.core.exception.MalformedLogFileException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.*;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;

public interface CryptoProvider {
    int LENGTH_CODE_SIZE = 4;

    void encryptEncStream(RSAPublicKey publicKey, InputStream inputStream, OutputStream outputStream, String fileExtension)
            throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException,
            InvalidKeyException, InvalidKeySpecException, BadPaddingException, IllegalBlockSizeException, IOException;

    String decryptEncStream(RSAPrivateKey privateKey, InputStream inputStream, OutputStream outputStream)
            throws MalformedLogFileException, IOException, NoSuchPaddingException,
            NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException,
            InvalidAlgorithmParameterException;

    void encryptLogStream(RSAPublicKey publicRsaKey, String text, OutputStream fos) throws NoSuchAlgorithmException,
            NoSuchPaddingException, InvalidAlgorithmParameterException, InvalidKeyException, IOException,
            BadPaddingException, IllegalBlockSizeException;

    void decryptLogStream(RSAPrivateKey privateKey, InputStream inputStream, PrintStream outputStream) throws IOException,
            InvalidAlgorithmParameterException, MalformedLogFileException,
            NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException, InvalidKeyException;

    boolean logHasNext(InputStream logInputStream, byte[] lc1Bytes) throws IOException, InvalidAlgorithmParameterException;

    LogDataBlocksAndText getNextLogTextWithDataBlocks(InputStream inputStream, RSAPrivateKey privateKey, byte[] lc1Bytes)
            throws IOException, MalformedLogFileException, IllegalBlockSizeException,
            InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException,
            InvalidAlgorithmParameterException;

    RSAPublicKey getPublicKeyFromBytes(byte[] publicKey) throws NoSuchAlgorithmException, InvalidKeySpecException;

    RSAPrivateKey getPrivateKeyFromBytes(byte[] privateKey) throws NoSuchAlgorithmException, InvalidKeySpecException;

    KeyPair generateKeyPair() throws NoSuchAlgorithmException;

    KeyPair getKeyPairFromBytes(byte[] privateKey, byte[] publicKey) throws NoSuchAlgorithmException,
            InvalidKeySpecException;

    ///TODO we can and should do away with passcheck all together by generating and encrypting one on the fly
    boolean checkPassword(char[] password, String passCheck, String encryptedPassCheck)
            throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException, InvalidAlgorithmParameterException,
            InvalidKeyException, BadPaddingException, IllegalBlockSizeException;

    byte[] decryptEncodedKey(char[] password, String encodedKey) throws InvalidKeySpecException,
            InvalidAlgorithmParameterException, NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException,
            BadPaddingException, IllegalBlockSizeException;

    EncodedKeyInfo getEncodedKeyInfo(char[] password, String passCheck, KeyPair keyPair)
            throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException,
            InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException;
}
