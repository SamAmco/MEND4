package co.samco.mend4.core.crypto.impl;

import co.samco.mend4.core.IBase64EncodingProvider;
import co.samco.mend4.core.bean.EncodedKeyInfo;
import co.samco.mend4.core.bean.LogDataBlocks;
import co.samco.mend4.core.bean.LogDataBlocksAndText;
import co.samco.mend4.core.crypto.CryptoProvider;
import co.samco.mend4.core.exception.MalformedLogFileException;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class DefaultJCECryptoProvider implements CryptoProvider {
    private final IvParameterSpec iv;
    private final int aesKeySize;
    private final int rsaKeySize;
    private final String aesAlgorithm;
    private final String rsaAlgorithm;
    private final byte[] passcheckSalt;
    private final int aesKeyGenIterations;
    private final IBase64EncodingProvider encoder;

    public DefaultJCECryptoProvider(IvParameterSpec iv, int aesKeySize, int rsaKeySize, String aesAlgorithm,
                                    String rsaAlgorithm, byte[] passcheckSalt, int aesKeyGenIterations,
                                    IBase64EncodingProvider encoder) {
        this.iv = iv;
        this.aesKeySize = aesKeySize;
        this.rsaKeySize = rsaKeySize;
        this.aesAlgorithm = aesAlgorithm;
        this.rsaAlgorithm = rsaAlgorithm;
        this.passcheckSalt = passcheckSalt;
        this.aesKeyGenIterations = aesKeyGenIterations;
        this.encoder = encoder;
    }

    private SecretKey generateAesKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(aesKeySize);
        return keyGen.generateKey();
    }

    private SecretKey getAesKeyFromBytes(byte[] aesKeyBytes) {
        return new SecretKeySpec(aesKeyBytes, 0, aesKeyBytes.length, "AES");
    }

    private Cipher getAesCipherFromPassword(char[] password, int mode) throws NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeySpecException, InvalidAlgorithmParameterException, InvalidKeyException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        KeySpec spec = new PBEKeySpec(password, passcheckSalt, aesKeyGenIterations, aesKeySize);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKey aesKey = new SecretKeySpec(tmp.getEncoded(), "AES");
        Cipher aesCipher = Cipher.getInstance(aesAlgorithm);
        aesCipher.init(mode, aesKey, iv);
        return aesCipher;
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

    private byte[] getIntAsLengthCode(int number) {
        return ByteBuffer.allocate(LENGTH_CODE_SIZE).putInt(number).array();
    }

    private int getLengthCodeAsInt(byte[] bytes) {
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

    private byte[] readMendFileBytes(InputStream inputStream, int number) throws MalformedLogFileException, IOException {
        byte[] bytes = new byte[number];
        if (inputStream.read(bytes) != number)
            throw new MalformedLogFileException();
        return bytes;
    }

    private LogDataBlocks getNextLogBytes(InputStream inputStream, byte[] lc1Bytes) throws IOException,
            MalformedLogFileException {
        byte[] encAesKey = readMendFileBytes(inputStream, getLengthCodeAsInt(lc1Bytes));
        byte[] lc2Bytes = readMendFileBytes(inputStream, LENGTH_CODE_SIZE);
        byte[] encEntry = readMendFileBytes(inputStream, getLengthCodeAsInt(lc2Bytes));
        return new LogDataBlocks(lc1Bytes, encAesKey, lc2Bytes, encEntry);
    }

    @Override
    public void encryptEncStream(RSAPublicKey publicKey, InputStream inputStream,
                                 OutputStream outputStream, String fileExtension)
            throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException,
            InvalidKeyException, BadPaddingException, IllegalBlockSizeException, IOException {
        SecretKey aesKey = generateAesKey();
        Cipher rsaCipher = getRsaEncrytpCipher(publicKey);

        byte[] encryptedAesKey = rsaCipher.doFinal(aesKey.getEncoded());
        byte[] lengthCode1 = getIntAsLengthCode(encryptedAesKey.length);
        byte[] fileExtensionBytes = rsaCipher.doFinal(fileExtension.getBytes());

        outputStream.write(lengthCode1);
        outputStream.write(encryptedAesKey);
        outputStream.write(getIntAsLengthCode(fileExtensionBytes.length));
        outputStream.write(fileExtensionBytes);

        writeToCipherStream(inputStream, new CipherOutputStream(outputStream, getAesEncryptCipher(aesKey)));
    }

    @Override
    public String decryptEncStream(RSAPrivateKey privateKey, InputStream inputStream, OutputStream outputStream)
            throws MalformedLogFileException, IOException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException {
        Cipher rsaCipher = getRsaDecryptCipher(privateKey);

        byte[] encAesKey = readMendFileBytes(inputStream, getLengthCodeAsInt(readMendFileBytes(inputStream, LENGTH_CODE_SIZE)));
        byte[] aesKeyBytes = rsaCipher.doFinal(encAesKey);
        SecretKey aesKey = getAesKeyFromBytes(aesKeyBytes);
        byte[] encFileExtension = readMendFileBytes(inputStream, getLengthCodeAsInt(readMendFileBytes(inputStream, LENGTH_CODE_SIZE)));
        String fileExtension = new String(rsaCipher.doFinal(encFileExtension));

        writeToCipherStream(inputStream, new CipherOutputStream(outputStream, getAesDecryptCipher(aesKey)));
        return fileExtension;
    }

    @Override
    public LogDataBlocksAndText getNextLogTextWithDataBlocks(InputStream inputStream,
                                                             RSAPrivateKey privateKey, byte[] lc1Bytes)
            throws IOException, MalformedLogFileException, IllegalBlockSizeException, InvalidKeyException,
            BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException {
        LogDataBlocks ldb = getNextLogBytes(inputStream, lc1Bytes);
        SecretKey aesKey = decryptAesKeyFromLog(ldb, privateKey);
        byte[] entry = decryptEntryFromLog(ldb, aesKey);
        return new LogDataBlocksAndText(ldb, new String(entry, StandardCharsets.UTF_8));
    }

    @Override
    public RSAPublicKey getPublicKeyFromBytes(byte[] publicKey) throws NoSuchAlgorithmException,
            InvalidKeySpecException {
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(publicKey));
    }

    @Override
    public RSAPrivateKey getPrivateKeyFromBytes(byte[] privateKey) throws NoSuchAlgorithmException,
            InvalidKeySpecException {
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(privateKey));
    }

    @Override
    public void decryptLogStream(RSAPrivateKey privateKey, InputStream inputStream, PrintStream outputStream) throws
            IOException, InvalidAlgorithmParameterException, MalformedLogFileException, NoSuchAlgorithmException,
            IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException, InvalidKeyException {
        byte[] lc1Bytes = new byte[LENGTH_CODE_SIZE];
        while (logHasNext(inputStream, lc1Bytes)) {
            outputStream.println(getNextLogTextWithDataBlocks(inputStream, privateKey, lc1Bytes).getEntryText());
            outputStream.println();
        }
    }

    @Override
    public void encryptLogStream(RSAPublicKey publicKey, String text, OutputStream outputStream)
            throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException,
            InvalidKeyException, IOException, BadPaddingException, IllegalBlockSizeException {
        SecretKey aesKey = generateAesKey();
        Cipher aesCipher = getAesEncryptCipher(aesKey);
        Cipher rsaCipher = getRsaEncrytpCipher(publicKey);

        byte[] cipherText = aesCipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
        byte[] encryptedAesKey = rsaCipher.doFinal(aesKey.getEncoded());
        byte[] lengthCode1 = ByteBuffer.allocate(LENGTH_CODE_SIZE).putInt(encryptedAesKey.length).array();
        byte[] lengthCode2 = ByteBuffer.allocate(LENGTH_CODE_SIZE).putInt(cipherText.length).array();

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


    @Override
    public boolean logHasNext(InputStream logInputStream, byte[] lc1Bytes)
            throws IOException, InvalidAlgorithmParameterException {
        if (lc1Bytes == null || lc1Bytes.length != LENGTH_CODE_SIZE)
            throw new InvalidAlgorithmParameterException("Expected lc1Bytes to be initialized and have length "
                    + LENGTH_CODE_SIZE);
        return logInputStream.read(lc1Bytes) == LENGTH_CODE_SIZE;
    }

    @Override
    public KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen;
        keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(rsaKeySize);
        return keyGen.genKeyPair();
    }

    @Override
    public KeyPair getKeyPairFromBytes(byte[] privateKey, byte[] publicKey) throws NoSuchAlgorithmException,
            InvalidKeySpecException {
        KeyFactory kf = KeyFactory.getInstance("RSA");
        RSAPrivateKey rsaPrivateKey = (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(privateKey));
        RSAPublicKey rsaPublicKey = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(publicKey));
        return new KeyPair(rsaPublicKey, rsaPrivateKey);
    }

    @Override
    public EncodedKeyInfo getEncodedKeyInfo(char[] password, String passCheck, KeyPair keyPair)
            throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException,
            InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        Cipher aesCipher = getAesCipherFromPassword(password, Cipher.ENCRYPT_MODE);
        byte[] encryptedPrivateKey = aesCipher.doFinal(keyPair.getPrivate().getEncoded());
        byte[] cipherText = aesCipher.doFinal(passCheck.getBytes(StandardCharsets.UTF_8));
        return new EncodedKeyInfo(encoder.encodeBase64URLSafeString(encryptedPrivateKey),
                encoder.encodeBase64URLSafeString(keyPair.getPublic().getEncoded()),
                encoder.encodeBase64URLSafeString(cipherText));
    }

    @Override
    public boolean checkPassword(char[] password, String passCheck, String encryptedPassCheck)
            throws InvalidKeySpecException, NoSuchPaddingException, InvalidAlgorithmParameterException, InvalidKeyException,
            BadPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException {
        byte[] cipherTextBytes = encoder.decodeBase64(encryptedPassCheck);
        byte[] plainText = getAesCipherFromPassword(password, Cipher.DECRYPT_MODE).doFinal(cipherTextBytes);
        return passCheck.equals(new String(plainText, StandardCharsets.UTF_8));
    }

    @Override
    public byte[] decryptEncodedKey(char[] password, String encodedKey) throws InvalidKeySpecException,
            InvalidAlgorithmParameterException, NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException,
            BadPaddingException, IllegalBlockSizeException {
        return getAesCipherFromPassword(password, Cipher.DECRYPT_MODE).doFinal(encoder.decodeBase64(encodedKey));
    }
}





