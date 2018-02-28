package co.samco.mend4.core;

import co.samco.mend4.core.bean.LogDataBlocksAndText;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.*;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;

public interface CryptoProvider {
    void encryptEncStream(RSAPublicKey publicKey, InputStream inputStream, OutputStream outputStream)
            throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException,
            InvalidKeyException, InvalidKeySpecException, BadPaddingException, IllegalBlockSizeException, IOException;

    String decryptEncStream(RSAPrivateKey privateKey, InputStream inputStream, OutputStream outputStream)
            throws DefaultJCECryptoProvider.MalformedLogFileException, IOException, NoSuchPaddingException,
            NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException,
            InvalidAlgorithmParameterException;

    LogDataBlocksAndText getNextLogTextWithDataBlocks(InputStream inputStream, RSAPrivateKey privateKey, byte[] lc1Bytes)
            throws IOException, DefaultJCECryptoProvider.MalformedLogFileException, IllegalBlockSizeException,
            InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException,
            InvalidAlgorithmParameterException;

    void decryptLog(InputStream inputStream, PrintStream outputStream, RSAPrivateKey privateKey) throws IOException,
            InvalidAlgorithmParameterException, DefaultJCECryptoProvider.MalformedLogFileException,
            NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException, InvalidKeyException;

    void encryptLogToStream(RSAPublicKey publicRsaKey, OutputStream fos, char[] text) throws NoSuchAlgorithmException,
            NoSuchPaddingException, InvalidAlgorithmParameterException, InvalidKeyException, IOException,
            BadPaddingException, IllegalBlockSizeException;
}
