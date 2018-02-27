package co.samco.mend4.core;

import co.samco.mend4.core.bean.LogDataBlocksAndText;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

public interface CryptoProvider {
    void encryptEncStream(X509EncodedKeySpec publicKey, InputStream inputStream, OutputStream outputStream) throws DefaultJCECryptoProvider.MissingPublicKeyException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException, InvalidKeyException, InvalidKeySpecException, BadPaddingException, IllegalBlockSizeException, IOException;
    LogDataBlocksAndText getNextLogTextWithDataBlocks(InputStream inputStream, RSAPrivateKey privateKey, byte[] lc1Bytes);
    void decryptLog(InputStream inputStream, PrintStream outputStream, RSAPrivateKey privateKey);
    void encryptLogToStream(IBase64EncodingProvider encoder, OutputStream fos, char[] text, boolean dropHeader);
}
