package co.samco.mend4.core;

import co.samco.mend4.core.bean.LogDataBlocks;
import co.samco.mend4.core.bean.LogDataBlocksAndText;

import java.io.*;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class DefaultJCECryptoProvider implements CryptoProvider {
    private final IvParameterSpec iv;
    private final int aesKeySize;
    private final String aesAlgorithm;
    private final String rsaAlgorithm;

    public DefaultJCECryptoProvider(IvParameterSpec iv, int aesKeySize, String aesAlgorithm, String rsaAlgorithm) {
        this.iv = iv;
        this.aesKeySize = aesKeySize;
        this.aesAlgorithm = aesAlgorithm;
        this.rsaAlgorithm = rsaAlgorithm;
    }

    private SecretKey generateAesKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(aesKeySize);
        return keyGen.generateKey();
    }

    private SecretKey getAesKeyFromBytes(byte[] aesKeyBytes) {
        return new SecretKeySpec(aesKeyBytes, 0, aesKeyBytes.length, "AES");
    }

    private Cipher getAesEncryptCipher(SecretKey aesKey) throws NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, InvalidKeyException {
        Cipher aesCipher = Cipher.getInstance(aesAlgorithm);
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, iv);
        return aesCipher;
    }

    private Cipher getAesDecryptCipher(SecretKey aesKey) throws NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, InvalidKeyException {
        Cipher aesCipher = Cipher.getInstance(aesAlgorithm);
        aesCipher.init(Cipher.DECRYPT_MODE, aesKey, iv);
        return aesCipher;
    }

    private Cipher getRsaEncrytpCipher(RSAPublicKey publicKey) throws InvalidKeyException,
            NoSuchPaddingException, NoSuchAlgorithmException {
        Cipher rsaCipher = Cipher.getInstance(rsaAlgorithm);
        rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return rsaCipher;
    }

    private Cipher getRsaDecryptCipher(RSAPrivateKey privateKey) throws NoSuchPaddingException,
            NoSuchAlgorithmException, InvalidKeyException {
        Cipher rsaCipher = Cipher.getInstance(rsaAlgorithm);
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
        return rsaCipher;
    }

    private SecretKey decryptAesKeyFromLog(LogDataBlocks ldb, RSAPrivateKey privateKey) throws NoSuchPaddingException,
            NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        Cipher rsaCipher = getRsaDecryptCipher(privateKey);
        byte[] aesKeyBytes = rsaCipher.doFinal(ldb.getEncAesKey());
        return getAesKeyFromBytes(aesKeyBytes);
    }

    private byte[] decryptEntryFromLog(LogDataBlocks ldb, SecretKey aesKey) throws InvalidAlgorithmParameterException,
            NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException, BadPaddingException,
            IllegalBlockSizeException {
        Cipher aesCipher = getAesDecryptCipher(aesKey);
        return aesCipher.doFinal(ldb.getEncEntry());
    }

    private byte[] readBytes(InputStream inputStream, int number) throws MalformedLogFileException, IOException {
        byte[] bytes = new byte[number];
        if (inputStream.read(bytes) != number)
            throw new MalformedLogFileException();
        return bytes;
    }

    private byte[] getIntAs4Bytes(int number) {
        return ByteBuffer.allocate(4).putInt(number).array();
    }

    private int get4BytesAsInt(byte[] bytes) {
        //big-endian by default
        return ByteBuffer.wrap(bytes).getInt();
    }

    private void writeToCipherStream(InputStream inputStream, CipherOutputStream cipherOutputStream) throws IOException {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = inputStream.read(buffer)) > 0) {
            cipherOutputStream.write(buffer, 0, count);
        }
    }

    @Override
    public void encryptEncStream(RSAPublicKey publicKey, InputStream inputStream, OutputStream outputStream) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException, InvalidKeyException, InvalidKeySpecException, BadPaddingException, IllegalBlockSizeException, IOException {
        SecretKey aesKey = generateAesKey();
        Cipher rsaCipher = getRsaEncrytpCipher(publicKey);

        byte[] encryptedAesKey = rsaCipher.doFinal(aesKey.getEncoded());
        byte[] lengthCode1 = ByteBuffer.allocate(4).putInt(encryptedAesKey.length).array();
        byte[] fileExtensionBytes = rsaCipher.doFinal(AppProperties.ENC_FILE_EXTENSION.getBytes());

        outputStream.write(lengthCode1);
        outputStream.write(encryptedAesKey);
        outputStream.write(getIntAs4Bytes(fileExtensionBytes.length));
        outputStream.write(fileExtensionBytes);

        writeToCipherStream(inputStream, new CipherOutputStream(outputStream, getAesEncryptCipher(aesKey)));
    }

    @Override
    public String decryptEncStream(RSAPrivateKey privateKey, InputStream inputStream, OutputStream outputStream) throws MalformedLogFileException, IOException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException {
        Cipher rsaCipher = getRsaDecryptCipher(privateKey);

        byte[] encAesKey = readBytes(inputStream, get4BytesAsInt(readBytes(inputStream, 4)));
        byte[] aesKeyBytes = rsaCipher.doFinal(encAesKey);
        SecretKey aesKey = getAesKeyFromBytes(aesKeyBytes);
        byte[] encFileExtension = readBytes(inputStream, get4BytesAsInt(readBytes(inputStream, 4)));
        String fileExtension = new String(rsaCipher.doFinal(encFileExtension));

        writeToCipherStream(inputStream, new CipherOutputStream(outputStream, getAesDecryptCipher(aesKey)));
        return fileExtension;
    }

    @Override
    public LogDataBlocksAndText getNextLogTextWithDataBlocks(InputStream inputStream, RSAPrivateKey privateKey, byte[] lc1Bytes) throws IOException, MalformedLogFileException, IllegalBlockSizeException, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException {
        LogDataBlocks ldb = getNextLogBytes(inputStream, lc1Bytes);
        SecretKey aesKey = decryptAesKeyFromLog(ldb, privateKey);
        byte[] entry = decryptEntryFromLog(ldb, aesKey);
        return new LogDataBlocksAndText(ldb, new String(entry, "UTF-8"));
    }

    @Override
    public void decryptLog(InputStream inputStream, PrintStream outputStream, RSAPrivateKey privateKey) throws
            IOException, InvalidAlgorithmParameterException, MalformedLogFileException, NoSuchAlgorithmException,
            IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException, InvalidKeyException {
        byte[] lc1Bytes = new byte[4];
        while (logHasNext(inputStream, lc1Bytes)) {
            outputStream.println(getNextLogTextWithDataBlocks(inputStream, privateKey, lc1Bytes).getEntryText());
            outputStream.println();
        }
    }

    @Override
    public void encryptLogToStream(RSAPublicKey publicKey, OutputStream outputStream, char[] text) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException, InvalidKeyException, IOException, BadPaddingException, IllegalBlockSizeException {
        SecretKey aesKey = generateAesKey();
        Cipher aesCipher = getAesEncryptCipher(aesKey);
        Cipher rsaCipher = getRsaEncrytpCipher(publicKey);

        byte[] cipherText = aesCipher.doFinal(new String(text).getBytes("UTF-8"));
        byte[] encryptedAesKey = rsaCipher.doFinal(aesKey.getEncoded());
        byte[] lengthCode1 = ByteBuffer.allocate(4).putInt(encryptedAesKey.length).array();
        byte[] lengthCode2 = ByteBuffer.allocate(4).putInt(cipherText.length).array();

        ByteBuffer output = ByteBuffer.allocate(
                lengthCode1.length
                + encryptedAesKey.length
                + lengthCode2.length
                + cipherText.length);
        output.put(lengthCode1);
        output.put(encryptedAesKey);
        output.put(lengthCode2);
        output.put(cipherText);

        outputStream.write(output.array());
    }


    public static boolean logHasNext(InputStream logInputStream, byte[] lc1Bytes)
            throws IOException, InvalidAlgorithmParameterException {
        if (lc1Bytes == null || lc1Bytes.length != 4)
            throw new InvalidAlgorithmParameterException("Expected lc1Bytes to be initialized and have length 4");
        return logInputStream.read(lc1Bytes) == 4;
    }

    private LogDataBlocks getNextLogBytes(InputStream inputStream, byte[] lc1Bytes) throws IOException,
            MalformedLogFileException {
        byte[] encAesKey = readBytes(inputStream, get4BytesAsInt(lc1Bytes));
        byte[] lc2Bytes = readBytes(inputStream, 4);
        byte[] encEntry = readBytes(inputStream, get4BytesAsInt(lc2Bytes));
        return new LogDataBlocks(lc1Bytes, encAesKey, lc2Bytes, encEntry);
    }

    public static class MalformedLogFileException extends Exception {
        private static final long serialVersionUID = 9219333934024822210L;

        public MalformedLogFileException() {
            super("Log file is malformed.");
        }
    }
}





