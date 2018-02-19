package co.samco.mend4.desktop.helper;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.EncryptionUtils;
import co.samco.mend4.core.impl.SettingsImpl;
import co.samco.mend4.desktop.core.ApacheCommonsEncoder;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FilenameUtils;

import javax.crypto.*;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import java.awt.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

//TODO refactor this whole class, it's an un-godly mess
public class CryptoHelper {

    private final PrintStreamProvider log;
    private final FileResolveHelper fileResolveHelper;

    @Inject
    public CryptoHelper(PrintStreamProvider log, FileResolveHelper fileResolveHelper) {
        this.log = log;
        this.fileResolveHelper = fileResolveHelper;
    }

    public void encryptFile(String filePath, String name) {
        //If there isn't a file name given, then let's generate a name for the file using a timestamp (16 digits).
        if (name == null)
            name = new SimpleDateFormat("yyyyMMddHHmmssSS").format(new Date());

        File fileToEncrypt = new File(filePath);
        if (!fileToEncrypt.exists()) {
            System.err.println("Could not find file.");
            return;
        }

        FileInputStream fis = null;
        try {
            FileOutputStream fos = null;
            try {
                //Make sure you're able to set up a file input stream
                fis = new FileInputStream(fileToEncrypt);
                //Check that you're able to set up an output stream
                //TODO only commented to compile
                String encLocation = "";//SettingsImpl.instance().getValue(Config.Settings.ENCDIR);
                if (encLocation == null) {
                    //TODO only commented to compile
                    throw new IOException("You need to set the " //Config.SETTINGS_NAMES_MAP.get(Config.Settings.ENCDIR.ordinal())
                            + " property in your settings file before you can encrypt files to it.");
                }
                File outputFile = new File(encLocation + File.separatorChar + name + ".enc");
                if (outputFile.exists()) {
                    System.err.println("The output file already exists: " + outputFile.getAbsolutePath());
                }
                fos = new FileOutputStream(outputFile);

                String fileExtension = FilenameUtils.getExtension(fileToEncrypt.getAbsolutePath());

                //now we can append all the encrypted file bytes
                System.err.println("Encrypting file to: " + outputFile.getAbsolutePath());
                EncryptionUtils.encryptFileToStream(new ApacheCommonsEncoder(), fis, fos, fileExtension);
                System.err.println("Encryption complete. Key: " + name);

            } finally {
                if (fos != null)
                    fos.close();
            }
        } catch (SettingsImpl.CorruptSettingsException | SettingsImpl.InvalidSettingNameException
                | SettingsImpl.UnInitializedSettingsException | IOException | InvalidKeyException
                | NoSuchAlgorithmException | NoSuchPaddingException | InvalidAlgorithmParameterException
                | InvalidKeySpecException | IllegalBlockSizeException | BadPaddingException e) {
            System.err.println(e.getMessage());
        } catch (EncryptionUtils.MissingPublicKeyException e) {
            System.err.println("Failed to find your public key. Please ensure you have run \"mend setup\" "
                    + "and that your Settings are not corrupt or in-accessable to mend");
        } finally {
            try {
                if (fis != null)
                    fis.close();
            } catch (IOException e1) {
                System.err.println("MEND encountered an error closing file input stream.");
                e1.printStackTrace();
            }
        }
    }

    public void encryptTextToLog(char[] text, boolean dropHeader) {
        if (text.length <= 0)
            return;

        FileOutputStream fos = null;
        try {
            //TODO only commented to compile
            String logDir = "";//SettingsImpl.instance().getValue(Config.Settings.LOGDIR);
            //TODO only commented to compile
            String currentLogName = "";//SettingsImpl.instance().getValue(Config.Settings.CURRENTLOG);
            if (logDir == null)
                //TODO only commented to compile
                throw new IOException("You need to set the " //+ Config.SETTINGS_NAMES_MAP.get(Config.Settings.LOGDIR .ordinal())
                        + " property in your settings file before you can encrypt logs to it.");
            if (currentLogName == null) {
                //TODO only commented to compile
                //SettingsImpl.instance().setValue(Config.Settings.CURRENTLOG, "Log");
                currentLogName = "Log";
            }

            if (currentLogName.length() < 1)
                currentLogName = "Log";
            if (currentLogName.length() < 6 || !currentLogName.substring(currentLogName.length() - 5, currentLogName
                    .length()).equals(".mend"))
                currentLogName += ".mend";

            File currentLogFile = new File(logDir + File.separatorChar + currentLogName);
            currentLogFile.createNewFile();
            fos = new FileOutputStream(currentLogFile, true);

            EncryptionUtils.encryptLogToStream(new ApacheCommonsEncoder(), fos, text, dropHeader);

            DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            Date date = new Date();
            System.out.println("Successfully Logged entry at: " + dateFormat.format(date));
        } catch (NoSuchAlgorithmException | IOException | InvalidKeyException
                | IllegalBlockSizeException | BadPaddingException
                | InvalidKeySpecException | SettingsImpl.CorruptSettingsException
                | SettingsImpl.InvalidSettingNameException | NoSuchPaddingException | InvalidAlgorithmParameterException
                | SettingsImpl.UnInitializedSettingsException e) {
            System.err.println(e.getMessage());
        } catch (EncryptionUtils.MissingPublicKeyException e) {
            System.err.println("Failed to find your public key. Please ensure you have run \"mend setup\" "
                    + "and that your Settings are not corrupt or in-accessable to mend");
        } finally {
            try {
                if (fos != null)
                    fos.close();
            } catch (IOException e1) {
                System.err.println("MEND encountered a fatal error.");
                e1.printStackTrace();
            }
        }
    }

    //TODO print some sort of helpful message if mend is not unlocked
    public void decryptLog(File file) {
        try {
            EncryptionUtils.decryptLog(file, fileResolveHelper.getPrivateKey(), System.out);
        }
        catch (IOException | EncryptionUtils.MalformedLogFileException
                | SettingsImpl.InvalidSettingNameException | SettingsImpl.CorruptSettingsException
                | SettingsImpl.UnInitializedSettingsException | InvalidKeyException
                | BadPaddingException | IllegalBlockSizeException | NoSuchPaddingException
                | NoSuchAlgorithmException | InvalidAlgorithmParameterException
                | InvalidKeySpecException e) {
            log.err().println(e.getMessage());
        }
    }

    //TODO print some sort of helpful message if mend is not unlocked
    public void decryptFile(File file, boolean silent) {
        FileInputStream fis = null;
        FileOutputStream fos = null;
        CipherOutputStream cos = null;
        try {
            File privateKeyFile = new File(Config.CONFIG_PATH + Config.PRIVATE_KEY_FILE_DEC);
            RSAPrivateKey privateKey = EncryptionUtils.getPrivateKeyFromFile(privateKeyFile);
            //TODO only commented to compile
            String decLocation = "";//SettingsImpl.instance().getValue(Config.Settings.DECDIR);
            if (decLocation == null)
                //TODO only commented to compile
                throw new FileNotFoundException("You need to set the " //+ Config.SETTINGS_NAMES_MAP.get(Config .Settings.DECDIR.ordinal())
                        + " property in your settings file before you can decrypt files to it.");

            decLocation += File.separatorChar;

            fis = new FileInputStream(file);
            byte[] lcBytes = new byte[4];
            //while there is an initial length code to be read in, read it in
            if (fis.read(lcBytes) != lcBytes.length)
                throw new EncryptionUtils.MalformedLogFileException("This enc file is malformed");

            //then convert it to an integer
            int lc = ByteBuffer.wrap(lcBytes).getInt(); //big-endian by default

            //and read in that many bytes as the encrypted aes key used to encrypt this enc file
            byte[] encAesKey = new byte[lc];
            if (fis.read(encAesKey) != encAesKey.length)
                throw new EncryptionUtils.MalformedLogFileException("This log file is malformed.");

            //now decrypt the aes key
            //TODO Should be getting this from settings not config
            Cipher rsaCipher = Cipher.getInstance(Config.PREFERRED_RSA_ALG);
            rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] aesKeyBytes = rsaCipher.doFinal(encAesKey);
            SecretKey aesKey = new SecretKeySpec(aesKeyBytes, 0, aesKeyBytes.length, "AES");

            //first get the file extension
            byte[] lc2Bytes = new byte[4];

            if (fis.read(lc2Bytes) != lc2Bytes.length)
                throw new EncryptionUtils.MalformedLogFileException("This enc file is malformed");

            int lc2 = ByteBuffer.wrap(lc2Bytes).getInt(); //big-endian by default
            byte[] encFileExtension = new byte[lc2];

            if (fis.read(encFileExtension) != encFileExtension.length)
                throw new EncryptionUtils.MalformedLogFileException("This enc file is malformed");

            String fileExtension = new String(rsaCipher.doFinal(encFileExtension));

            File outputFile = new File(decLocation + FilenameUtils.removeExtension(file.getName()) + '.' +
                    fileExtension);
            if (outputFile.getParentFile() == null || !outputFile.getParentFile().exists())
                throw new FileNotFoundException("Could not write to the decrypt location.");

            if (outputFile.exists()) {
                System.err.println("The output file already exists at: " + outputFile.getAbsolutePath());
                return;
            }

            fos = new FileOutputStream(outputFile);

            //now decrypt the file
            //TODO Should be getting this from settings not config
            Cipher aesCipher = Cipher.getInstance(Config.PREFERRED_AES_ALG);
            aesCipher.init(Cipher.DECRYPT_MODE, aesKey, Config.STANDARD_IV);
            cos = new CipherOutputStream(fos, aesCipher);

            System.err.println("Decrypting the file to: " + outputFile.getAbsolutePath());
            //now we can append all the decrypted file bytes
            byte[] buffer = new byte[8192];
            int count;
            while ((count = fis.read(buffer)) > 0) {
                cos.write(buffer, 0, count);
            }
            System.err.println("Decryption complete.");

            if (!silent)
                Desktop.getDesktop().open(outputFile);
        } catch (IOException | EncryptionUtils.MalformedLogFileException | NoSuchAlgorithmException
                | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException
                | BadPaddingException | InvalidAlgorithmParameterException | InvalidKeySpecException e) {
            System.err.println(e.getMessage());
        }
        finally {
            try {
                if (fis != null)
                    fis.close();
                if (cos != null)
                    cos.close();
                if (fos != null)
                    fos.close();
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
        }
    }

    public KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen;
        keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(Config.RSA_KEY_SIZE);
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
        KeySpec spec = new PBEKeySpec(password.toCharArray(), Config.PASSCHECK_SALT,
                Config.AES_KEY_GEN_ITERATIONS, Config.AES_KEY_SIZE);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKey aesKey = new SecretKeySpec(tmp.getEncoded(), "AES");
        Cipher aesCipher = Cipher.getInstance(Config.PREFERRED_AES_ALG);
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, Config.STANDARD_IV);

        //Encrypt the private key with the password.
        byte[] encryptedPrivateKey = aesCipher.doFinal(keyPair.getPrivate().getEncoded());
        //encrypt the text
        byte[] cipherText = aesCipher.doFinal(Config.PASSCHECK_TEXT.getBytes("UTF-8"));

        return new EncodedKeyInfo(Base64.encodeBase64URLSafeString(encryptedPrivateKey),
                Base64.encodeBase64URLSafeString(keyPair.getPublic().getEncoded()),
                Base64.encodeBase64URLSafeString(cipherText));
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
