package co.samco.mend4.core;

import java.io.*;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
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

import co.samco.mend4.core.Settings;
import co.samco.mend4.core.Settings.CorruptSettingsException;
import co.samco.mend4.core.Settings.InvalidSettingNameException;
import co.samco.mend4.core.Settings.UnInitializedSettingsException;

public class EncryptionUtils {
    private static String addHeaderToLogText(char[] logText) throws UnInitializedSettingsException {
        StringBuilder sb = new StringBuilder();
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ENGLISH);
        sb.append(sdf.format(cal.getTime()));
        sb.append("//MEND" + Config.CORE_VERSION_NUMBER + "//");
        sb.append(Settings.instance().getPlatformDependentHeader());
        sb.append
                ("////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////\n");
        sb.append(logText);
        return sb.toString();
    }

    public static RSAPrivateKey getPrivateKeyFromFile(File privateKeyFile)
            throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
        FileInputStream privateKeyFileInputStream = null;
        try {
            //now read in the private rsa key.
            byte[] keyBytes = new byte[(int) privateKeyFile.length()];
            privateKeyFileInputStream = new FileInputStream(privateKeyFile);
            privateKeyFileInputStream.read(keyBytes);
            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) kf.generatePrivate(privateKeySpec);
        } finally {
            if (privateKeyFileInputStream != null)
                privateKeyFileInputStream.close();
        }
    }

    public static boolean logHasNext(FileInputStream logInputStream, byte[] lc1Bytes)
            throws IOException, InvalidAlgorithmParameterException {
        if (lc1Bytes == null || lc1Bytes.length != 4)
            throw new InvalidAlgorithmParameterException("Expected lc1Bytes to be initialized and have length 4");
        return logInputStream.read(lc1Bytes) == 4;
    }

    public static class LogDataBlocks {
        public byte[] encAesKey;
        public byte[] lc2Bytes;
        public byte[] encEntry;
        public byte[] lc1Bytes;

        public LogDataBlocks(byte[] lc1Bytes, byte[] encAesKey, byte[] lc2Bytes, byte[] encEntry) {
            this.lc1Bytes = lc1Bytes;
            this.encAesKey = encAesKey;
            this.lc2Bytes = lc2Bytes;
            this.encEntry = encEntry;
        }

        public byte[] getAsOneBlock() {
            byte[] combined = new byte[encAesKey.length + lc2Bytes.length + encEntry.length + lc2Bytes.length];
            byte[][] byteArrays = new byte[][]{lc1Bytes, encAesKey, lc2Bytes, encEntry};
            int end = 0;
            for (int i = 0; i < byteArrays.length; ++i) {
                System.arraycopy(byteArrays[i], 0, combined, end, byteArrays[i].length);
                end += byteArrays[i].length;
            }
            return combined;
        }
    }

    public static class LogDataBlocksAndText {
        public LogDataBlocks logDataBlocks;
        public String entryText;

        public LogDataBlocksAndText(LogDataBlocks logDataBlocks, String entryText) {
            this.logDataBlocks = logDataBlocks;
            this.entryText = entryText;
        }
    }

    public static LogDataBlocks getNextLogBytes(FileInputStream inputStream, byte[] lc1Bytes) throws IOException,
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
            privateKey,
                                                                    byte[] lc1Bytes) throws IOException,
            MalformedLogFileException, InvalidSettingNameException,
            CorruptSettingsException, UnInitializedSettingsException, NoSuchPaddingException,
            NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException,
            InvalidAlgorithmParameterException {
        LogDataBlocks ldb = getNextLogBytes(inputStream, lc1Bytes);

        //now decrypt the aes key
        Cipher rsaCipher = Cipher.getInstance(Config.PREFERRED_RSA_ALG());
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] aesKeyBytes = rsaCipher.doFinal(ldb.encAesKey);
        SecretKey aesKey = new SecretKeySpec(aesKeyBytes, 0, aesKeyBytes.length, "AES");

        //now decrypt the entry
        Cipher aesCipher = Cipher.getInstance(Config.PREFERRED_AES_ALG());
        aesCipher.init(Cipher.DECRYPT_MODE, aesKey, Config.STANDARD_IV);
        byte[] entry = aesCipher.doFinal(ldb.encEntry);
        return new LogDataBlocksAndText(ldb, new String(entry, "UTF-8"));
    }

    public static String getNextLogText(FileInputStream inputStream, RSAPrivateKey privateKey,
                                        byte[] lc1Bytes) throws IOException, MalformedLogFileException,
            InvalidSettingNameException,
            CorruptSettingsException, UnInitializedSettingsException, NoSuchPaddingException,
            NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException,
            InvalidAlgorithmParameterException {
        return getNextLogTextWithDataBlocks(inputStream, privateKey, lc1Bytes).entryText;
    }

    public static void decryptLog(File file, RSAPrivateKey privateKey, PrintStream outputStream)
            throws IOException, MalformedLogFileException, InvalidSettingNameException,
            CorruptSettingsException, UnInitializedSettingsException, InvalidKeyException,
            BadPaddingException, IllegalBlockSizeException, NoSuchPaddingException,
            NoSuchAlgorithmException, InvalidAlgorithmParameterException {
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

    public static void encryptFileToStream(IBase64EncodingProvider encoder, FileInputStream fis, FileOutputStream
            fos, String fileExtension)
            throws CorruptSettingsException, InvalidSettingNameException, UnInitializedSettingsException,
            MissingPublicKeyException,
            NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
            InvalidAlgorithmParameterException, InvalidKeySpecException,
            IllegalBlockSizeException, BadPaddingException, IOException {
        CipherOutputStream cos = null;
        try {
            String userPublicKeyString = Settings.instance().getValue(Config.Settings.PUBLICKEY);
            if (userPublicKeyString == null)
                throw new MissingPublicKeyException();

            //generate an aes key
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(Config.AES_KEY_SIZE());
            SecretKey aesKey = keyGen.generateKey();

            Cipher aesCipher = Cipher.getInstance(Config.PREFERRED_AES_ALG());
            aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, Config.STANDARD_IV);
            cos = new CipherOutputStream(fos, aesCipher);

            //encrypt the aes key with the public rsa key
            Cipher rsaCipher = Cipher.getInstance(Config.PREFERRED_RSA_ALG());

            //read in the public key
            X509EncodedKeySpec publicRsaKeySpec = new X509EncodedKeySpec(encoder.decodeBase64(userPublicKeyString));
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicRsaKey = keyFactory.generatePublic(publicRsaKeySpec);
            rsaCipher.init(Cipher.ENCRYPT_MODE, publicRsaKey);
            byte[] encryptedAesKey = rsaCipher.doFinal(aesKey.getEncoded());

            //generate length codes
            byte[] lengthCode1 = ByteBuffer.allocate(4).putInt(encryptedAesKey.length).array();

            //create an output byte array
            ByteBuffer output = ByteBuffer.allocate(lengthCode1.length + encryptedAesKey.length);

            //add the first length code to the output
            output.put(lengthCode1);

            //add the encrypted aes key to the byte block
            output.put(encryptedAesKey);

            //write the key length and encrypted key to the file
            fos.write(output.array());

            byte[] fileExtensionBytes = rsaCipher.doFinal(fileExtension.getBytes());
            fos.write(ByteBuffer.allocate(4).putInt(fileExtensionBytes.length).array());
            fos.write(fileExtensionBytes);

            byte[] buffer = new byte[8192];
            int count;
            while ((count = fis.read(buffer)) > 0) {
                cos.write(buffer, 0, count);
            }
        } finally {
            if (cos != null)
                cos.close();
        }
    }

    public static void encryptLogToStream(IBase64EncodingProvider encoder, FileOutputStream fos, char[] text, boolean
            dropHeader)
            throws CorruptSettingsException, InvalidSettingNameException, UnInitializedSettingsException,
            NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException,
            IllegalBlockSizeException, BadPaddingException, InvalidKeySpecException, MissingPublicKeyException,
            IOException {
        //Lets just do some basic checks first
        String userPublicKeyString = Settings.instance().getValue(Config.Settings.PUBLICKEY);
        if (userPublicKeyString == null)
            throw new MissingPublicKeyException();

        //generate an aes key
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(Config.AES_KEY_SIZE());
        SecretKey aesKey = keyGen.generateKey();

        //use it to encrypt the text
        Cipher aesCipher = Cipher.getInstance(Config.PREFERRED_AES_ALG());
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, Config.STANDARD_IV);
        String logText;
        if (dropHeader)
            logText = new String(text);
        else
            logText = addHeaderToLogText(text);
        byte[] cipherText = aesCipher.doFinal(logText.getBytes("UTF-8"));

        //encrypt the aes key with the public rsa key
        Cipher rsaCipher = Cipher.getInstance(Config.PREFERRED_RSA_ALG());

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

    public static class MissingPublicKeyException extends Exception {
        private static final long serialVersionUID = 8041634883751009836L;

        public MissingPublicKeyException() {
            super("");
        }
    }
}





