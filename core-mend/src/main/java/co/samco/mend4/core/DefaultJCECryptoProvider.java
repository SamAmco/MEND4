package co.samco.mend4.core;

import co.samco.mend4.core.bean.LogDataBlocks;
import co.samco.mend4.core.bean.LogDataBlocksAndText;
import org.apache.commons.lang3.ArrayUtils;

import java.io.*;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;


//TODO this is now a total mess, all needs fixing and refactoring
public class DefaultJCECryptoProvider implements CryptoProvider {

    public DefaultJCECryptoProvider() {}

    private SecretKey generateAesKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(AppProperties.PREFERRED_AES_KEY_SIZE);
        return keyGen.generateKey();
    }

    private Cipher getAesEncryptCipher(SecretKey aesKey) throws NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, InvalidKeyException {
        Cipher aesCipher = Cipher.getInstance(AppProperties.PREFERRED_AES_ALG);
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, AppProperties.STANDARD_IV);
        return aesCipher;
    }

    private Cipher getRsaEncrytpCipher(X509EncodedKeySpec publicKey) throws InvalidKeyException,
            InvalidKeySpecException, NoSuchPaddingException, NoSuchAlgorithmException {
        Cipher rsaCipher = Cipher.getInstance(AppProperties.PREFERRED_RSA_ALG);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicRsaKey = keyFactory.generatePublic(publicKey);
        rsaCipher.init(Cipher.ENCRYPT_MODE, publicRsaKey);
        return rsaCipher;
    }

    private byte[] getIntAs4Bytes(int number) {
        return ByteBuffer.allocate(4).putInt(number).array();
    }

    private void writeToCipherStream(InputStream inputStream, CipherOutputStream cipherOutputStream) throws IOException {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = inputStream.read(buffer)) > 0) {
            cipherOutputStream.write(buffer, 0, count);
        }
    }

    @Override
    public void encryptEncStream(X509EncodedKeySpec publicKey, InputStream inputStream, OutputStream outputStream) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException, InvalidKeyException, InvalidKeySpecException, BadPaddingException, IllegalBlockSizeException, IOException {
        SecretKey aesKey = generateAesKey();
        Cipher aesCipher = getAesEncryptCipher(aesKey);
        Cipher rsaCipher = getRsaEncrytpCipher(publicKey);

        byte[] encryptedAesKey = rsaCipher.doFinal(aesKey.getEncoded());
        byte[] lengthCode1 = ByteBuffer.allocate(4).putInt(encryptedAesKey.length).array();
        byte[] fileExtensionBytes = rsaCipher.doFinal(AppProperties.ENC_FILE_EXTENSION.getBytes());
        outputStream.write(lengthCode1);
        outputStream.write(encryptedAesKey);
        outputStream.write(getIntAs4Bytes(fileExtensionBytes.length));
        outputStream.write(fileExtensionBytes);

        CipherOutputStream cipherOutputStream = new CipherOutputStream(outputStream, aesCipher);
        writeToCipherStream(inputStream, cipherOutputStream);
    }

    @Override
    public LogDataBlocksAndText getNextLogTextWithDataBlocks(InputStream inputStream, RSAPrivateKey privateKey, byte[] lc1Bytes) {
        return null;
    }

    @Override
    public void decryptLog(InputStream inputStream, PrintStream outputStream, RSAPrivateKey privateKey) {

    }

    @Override
    public void encryptLogToStream(IBase64EncodingProvider encoder, OutputStream fos, char[] text, boolean dropHeader) {

    }

    private String addHeaderToLogText(char[] logText) {
        StringBuilder sb = new StringBuilder();
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ENGLISH);
        sb.append(sdf.format(cal.getTime()));
        sb.append("//MEND" + AppProperties.CORE_VERSION_NUMBER + "//");
        //TODO only commented to compile
        //sb.append(SettingsImpl.instance().getPlatformDependentHeader());
        sb.append ("////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////\n");
        sb.append(logText);
        return sb.toString();
    }

    public static boolean logHasNext(FileInputStream logInputStream, byte[] lc1Bytes)
            throws IOException, InvalidAlgorithmParameterException {
        if (lc1Bytes == null || lc1Bytes.length != 4)
            throw new InvalidAlgorithmParameterException("Expected lc1Bytes to be initialized and have length 4");
        return logInputStream.read(lc1Bytes) == 4;
    }

    public byte[] getAsOneBlock(LogDataBlocks block) {
        return ArrayUtils.addAll(
                ArrayUtils.addAll(block.getLc1Bytes(), block.getEncAesKey()),
                ArrayUtils.addAll(block.getLc2Bytes(), block.getEncEntry()));
    }

    private LogDataBlocks getNextLogBytes(FileInputStream inputStream, byte[] lc1Bytes) throws IOException,
            MalformedLogFileException {
        ByteBuffer lc1Buff = ByteBuffer.wrap(lc1Bytes); //big-endian by default
        int lc1 = lc1Buff.getInt();

        //and read in that many bytes as the encrypted aes key used to encrypt this log entry
        byte[] encAesKey = new byte[lc1];
        if (inputStream.read(encAesKey) != encAesKey.length)
            throw new MalformedLogFileException("This log file is malformed.");

        //read in the next length code
        byte[] lc2Bytes = new byte[4];
        if (inputStream.read(lc2Bytes) != 4)
            throw new MalformedLogFileException("This log file is malformed.");

        //convert that to an integer
        ByteBuffer lc2Buff = ByteBuffer.wrap(lc2Bytes); //big-endian by default
        int lc2 = lc2Buff.getInt();

        //now we can read in that many bytes as the encrypted log data
        byte[] encEntry = new byte[lc2];
        if (inputStream.read(encEntry) != encEntry.length)
            throw new MalformedLogFileException("This log file is malformed.");

        return new LogDataBlocks(lc1Bytes, encAesKey, lc2Bytes, encEntry);
    }

    public static LogDataBlocksAndText getNextLogTextWithDataBlocks(FileInputStream inputStream, RSAPrivateKey
            privateKey, byte[] lc1Bytes) throws IOException,
            MalformedLogFileException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
            BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException {
        LogDataBlocks ldb = getNextLogBytes(inputStream, lc1Bytes);

        //now decrypt the aes key
        Cipher rsaCipher = Cipher.getInstance(AppProperties.PREFERRED_RSA_ALG);
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] aesKeyBytes = rsaCipher.doFinal(ldb.encAesKey);
        SecretKey aesKey = new SecretKeySpec(aesKeyBytes, 0, aesKeyBytes.length, "AES");

        //now decrypt the entry
        Cipher aesCipher = Cipher.getInstance(AppProperties.PREFERRED_AES_ALG);
        aesCipher.init(Cipher.DECRYPT_MODE, aesKey, AppProperties.STANDARD_IV);
        byte[] entry = aesCipher.doFinal(ldb.encEntry);
        return new LogDataBlocksAndText(ldb, new String(entry, "UTF-8"));
    }

    private String getNextLogText(FileInputStream inputStream, RSAPrivateKey privateKey, byte[] lc1Bytes)
            throws IOException, MalformedLogFileException, NoSuchPaddingException,
            NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException,
            InvalidAlgorithmParameterException {
        return getNextLogTextWithDataBlocks(inputStream, privateKey, lc1Bytes).entryText;
    }

    public static void decryptLog(File file, RSAPrivateKey privateKey, PrintStream outputStream)
            throws IOException, MalformedLogFileException, InvalidKeyException, BadPaddingException,
            IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException {
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(file);
            byte[] lc1Bytes = new byte[4];
            //while there is an initial length code to be read in, read it in
            while (logHasNext(inputStream, lc1Bytes)) {
                outputStream.println(getNextLogText(inputStream, privateKey, lc1Bytes));
                outputStream.println();
            }
        } finally {
            if (inputStream != null)
                inputStream.close();
        }
    }

    public static void encryptFileToStream(IBase64EncodingProvider encoder, FileInputStream fis, FileOutputStream fos,
                                           String fileExtension) throws MissingPublicKeyException, NoSuchAlgorithmException,
            NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, InvalidKeySpecException,
            IllegalBlockSizeException, BadPaddingException, IOException {
    }

    public static void encryptLogToStream(IBase64EncodingProvider encoder, FileOutputStream fos, char[] text, boolean
            dropHeader) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException,
            IllegalBlockSizeException, BadPaddingException, InvalidKeySpecException, MissingPublicKeyException, IOException {
        //Lets just do some basic checks first
        //TODO only commented to compile
        String userPublicKeyString = "";//SettingsImpl.instance().getValue(Config.Settings.PUBLICKEY);
        if (userPublicKeyString == null)
            throw new MissingPublicKeyException();

        //generate an aes key
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(AppProperties.PREFERRED_AES_KEY_SIZE);
        SecretKey aesKey = keyGen.generateKey();

        //use it to encrypt the text
        Cipher aesCipher = Cipher.getInstance(AppProperties.PREFERRED_AES_ALG);
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, AppProperties.STANDARD_IV);
        String logText;
        if (dropHeader)
            logText = new String(text);
        else
            logText = addHeaderToLogText(text);
        byte[] cipherText = aesCipher.doFinal(logText.getBytes("UTF-8"));

        //encrypt the aes key with the public rsa key
        Cipher rsaCipher = Cipher.getInstance(AppProperties.PREFERRED_RSA_ALG);

        //read in the public key
        X509EncodedKeySpec publicRsaKeySpec = new X509EncodedKeySpec(encoder.decodeBase64(userPublicKeyString));
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicRsaKey = keyFactory.generatePublic(publicRsaKeySpec);
        rsaCipher.init(Cipher.ENCRYPT_MODE, publicRsaKey);
        byte[] encryptedAesKey = rsaCipher.doFinal(aesKey.getEncoded());

        //generate length codes
        byte[] lengthCode1 = ByteBuffer.allocate(4).putInt(encryptedAesKey.length).array();
        byte[] lengthCode2 = ByteBuffer.allocate(4).putInt(cipherText.length).array();

        //create an output byte array
        ByteBuffer output = ByteBuffer.allocate(lengthCode1.length + encryptedAesKey.length + lengthCode2.length +
                cipherText.length);

        //add the first length code to the output
        output.put(lengthCode1);

        //add the encrypted aes key to the byte block
        output.put(encryptedAesKey);

        //add the second length code
        output.put(lengthCode2);

        //add the encrypted text to the byte block
        output.put(cipherText);

        //append the byte block to the current log
        fos.write(output.array());
    }

    public static class MalformedLogFileException extends Exception {
        private static final long serialVersionUID = 9219333934024822210L;

        public MalformedLogFileException(String message) {
            super(message);
        }
    }
}





