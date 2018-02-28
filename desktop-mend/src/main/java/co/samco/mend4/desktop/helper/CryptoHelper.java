package co.samco.mend4.desktop.helper;

import co.samco.mend4.core.AppProperties;
import co.samco.mend4.core.EncryptionUtils;
import co.samco.mend4.core.OSDao;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.crypto.CryptoProvider;
import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.core.exception.MalformedLogFileException;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import javax.crypto.*;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import java.awt.*;
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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

//TODO refactor this whole class, it's an un-godly mess
//TODO more helpful exception handling and throwing, most lower level exceptions should be caught on this layer
//TODO print some sort of helpful message if mend is not unlocked
public class CryptoHelper {

    private final I18N strings;
    private final PrintStreamProvider log;
    private final FileResolveHelper fileResolveHelper;
    private final Settings settings;
    private final CryptoProvider cryptoProvider;
    private final KeyHelper keyHelper;
    private final OSDao osDao;

    @Inject
    public CryptoHelper(I18N strings, PrintStreamProvider log, FileResolveHelper fileResolveHelper, Settings settings,
                        CryptoProvider cryptoProvider, KeyHelper keyHelper, OSDao osDao) {
        this.strings = strings;
        this.log = log;
        this.fileResolveHelper = fileResolveHelper;
        this.settings = settings;
        this.cryptoProvider = cryptoProvider;
        this.keyHelper = keyHelper;
        this.osDao = osDao;
    }

    public void encryptFile(File file, String name) throws IOException, CorruptSettingsException,
            InvalidKeySpecException, NoSuchAlgorithmException, IllegalBlockSizeException, InvalidKeyException,
            BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException {
        if (name == null)
            name = new SimpleDateFormat("yyyyMMddHHmmssSS").format(new Date());

        String fileExtension = FilenameUtils.getExtension(file.getAbsolutePath());
        String encLocation = settings.getValue(Settings.Name.ENCDIR);
        File outputFile = new File(encLocation + File.separatorChar + name + AppProperties.ENC_FILE_EXTENSION);
        fileResolveHelper.assertFileDoesNotExist(outputFile);

        try (FileInputStream fis = new FileInputStream(file);
            FileOutputStream fos = new FileOutputStream(outputFile)) {
            System.err.println("Encrypting file to: " + outputFile.getAbsolutePath());
            cryptoProvider.encryptEncStream(keyHelper.getPublicKey(), fis, fos, fileExtension);
            System.err.println("Encryption complete. Key: " + name);
        }
    }

    private String addHeaderToLogText(String logText, String platformHeader) {
        StringBuilder sb = new StringBuilder();
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat(AppProperties.LOG_DATE_FORMAT, Locale.ENGLISH);
        sb.append(sdf.format(cal.getTime()));
        sb.append(String.format(AppProperties.LOG_HEADER, AppProperties.CORE_VERSION_NUMBER, platformHeader));
        sb.append(strings.getNewLine());
        sb.append(logText);
        return sb.toString();
    }

    public void encryptTextToLog(char[] text, boolean dropHeader) throws IOException, CorruptSettingsException,
            InvalidKeySpecException, NoSuchAlgorithmException, IllegalBlockSizeException, InvalidKeyException,
            BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException {
        if (text.length <= 0)
            return;

        File currentLogFile = fileResolveHelper.getCurrentLogFile();
        currentLogFile.createNewFile();
        String logText = new String(text);
        if (!dropHeader) {
            addHeaderToLogText(logText, strings.get("TODO"));
        }

        try (FileOutputStream fos = new FileOutputStream(currentLogFile, true)) {
            cryptoProvider.encryptLogStream(keyHelper.getPublicKey(), logText, fos);
        }

        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date date = new Date();
        System.out.println("Successfully Logged entry at: " + dateFormat.format(date));
    }

    public void decryptLog(File file) throws InvalidKeySpecException, IOException, NoSuchAlgorithmException,
            MalformedLogFileException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException,
            InvalidAlgorithmParameterException {
        try (FileInputStream fis = new FileInputStream(file)){
            cryptoProvider.decryptLogStream(keyHelper.getPrivateKey(), fis, log.out());
        }
    }

    public void decryptFile(File file, boolean silent) throws IOException, CorruptSettingsException,
            InvalidKeySpecException, NoSuchAlgorithmException, MalformedLogFileException,
            InvalidAlgorithmParameterException, NoSuchPaddingException, BadPaddingException, IllegalBlockSizeException, InvalidKeyException {
        String decDir = settings.getValue(Settings.Name.DECDIR);
        fileResolveHelper.assertDirWritable(decDir);
        File outputFile = FileUtils.getFile(decDir, FilenameUtils.removeExtension(file.getName()));
        fileResolveHelper.assertFileDoesNotExist(outputFile);
        String fileExtension;

        System.err.println("Decrypting the file to: " + outputFile.getAbsolutePath());
        try (FileInputStream fis = new FileInputStream(file);
            FileOutputStream fos = new FileOutputStream(outputFile)) {
            fileExtension = cryptoProvider.decryptEncStream(keyHelper.getPrivateKey(), fis, fos);
        }
        osDao.renameFile(outputFile, outputFile.getName() + "." + fileExtension);
        System.err.println("Decryption complete.");

        if (!silent) {
            osDao.desktopOpenFile(outputFile);
        }
    }

    public byte[] decryptBytesWithPassword(byte[] ciphertext, char[] password) throws NoSuchAlgorithmException,
            InvalidKeySpecException, NoSuchPaddingException, InvalidAlgorithmParameterException, InvalidKeyException,
            BadPaddingException, IllegalBlockSizeException {
        //generate an aes key from the password
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        KeySpec spec = new PBEKeySpec(password, AppProperties.PASSCHECK_SALT, AppProperties.AES_KEY_GEN_ITERATIONS, AppProperties.PREFERRED_AES_KEY_SIZE);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKey aesKey = new SecretKeySpec(tmp.getEncoded(), "AES");

        //use it to decrypt the text
        Cipher aesCipher = Cipher.getInstance(AppProperties.PREFERRED_AES_ALG);
        aesCipher.init(Cipher.DECRYPT_MODE, aesKey, AppProperties.STANDARD_IV);
        return aesCipher.doFinal(ciphertext);
    }

    public KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen;
        keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(AppProperties.PREFERRED_RSA_KEY_SIZE);
        return keyGen.genKeyPair();
    }

    public KeyPair readKeyPairFromFiles(File privateKeyFile, File publicKeyFile)
            throws InvalidKeySpecException, IOException, NoSuchAlgorithmException {
        FileInputStream privateKeyFileInputStream = null;
        FileInputStream publicKeyFileInputStream = null;
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");

            //read in the private rsa key.
            byte[] keyBytes = new byte[(int) privateKeyFile.length()];
            privateKeyFileInputStream = new FileInputStream(privateKeyFile);
            privateKeyFileInputStream.read(keyBytes);
            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(keyBytes);
            RSAPrivateKey privateKey = (RSAPrivateKey) kf.generatePrivate(privateKeySpec);

            //read in the public rsa key.
            byte[] keyBytes2 = new byte[(int) publicKeyFile.length()];
            publicKeyFileInputStream = new FileInputStream(publicKeyFile);
            publicKeyFileInputStream.read(keyBytes2);
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(keyBytes2);
            RSAPublicKey publicKey = (RSAPublicKey) kf.generatePublic(publicKeySpec);

            return new KeyPair(publicKey, privateKey);
        } finally {
            if (privateKeyFileInputStream != null) {
                privateKeyFileInputStream.close();
            }
            if (publicKeyFileInputStream != null) {
                publicKeyFileInputStream.close();
            }
        }
    }

    public EncodedKeyInfo getEncodedKeyInfo(String password, KeyPair keyPair) throws NoSuchAlgorithmException,
            InvalidKeySpecException, NoSuchPaddingException, InvalidAlgorithmParameterException, InvalidKeyException,
            BadPaddingException, IllegalBlockSizeException, UnsupportedEncodingException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), AppProperties.PASSCHECK_SALT,
                AppProperties.AES_KEY_GEN_ITERATIONS, AppProperties.PREFERRED_AES_KEY_SIZE);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKey aesKey = new SecretKeySpec(tmp.getEncoded(), "AES");
        Cipher aesCipher = Cipher.getInstance(AppProperties.PREFERRED_AES_ALG);
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, AppProperties.STANDARD_IV);

        //Encrypt the private key with the password.
        byte[] encryptedPrivateKey = aesCipher.doFinal(keyPair.getPrivate().getEncoded());
        //encrypt the text
        byte[] cipherText = aesCipher.doFinal(AppProperties.PASSCHECK_TEXT.getBytes(StandardCharsets.UTF_8));

        return new EncodedKeyInfo(Base64.encodeBase64URLSafeString(encryptedPrivateKey),
                Base64.encodeBase64URLSafeString(keyPair.getPublic().getEncoded()),
                Base64.encodeBase64URLSafeString(cipherText));
    }

    private RSAPrivateKey getPrivateKeyFromFile(File privateKeyFile)
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


    public static class EncodedKeyInfo {
        private final String privateKey;
        private final String publicKey;
        private final String cipherText;

        public EncodedKeyInfo(String privateKey, String publicKey, String cipherText) {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
            this.cipherText = cipherText;
        }

        public String getPrivateKey() {
            return privateKey;
        }

        public String getPublicKey() {
            return publicKey;
        }

        public String getCipherText() {
            return cipherText;
        }
    }
}
