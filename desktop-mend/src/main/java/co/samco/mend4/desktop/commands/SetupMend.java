package co.samco.mend4.desktop.commands;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;

import co.samco.mend4.core.Config;

import co.samco.mend4.core.Settings;
import co.samco.mend4.core.impl.SettingsImpl;
import co.samco.mend4.desktop.dao.OSDao;
import dagger.Lazy;

public class SetupMend extends Command {
    public static final String COMMAND_NAME = "setup";
    public static final String FORCE_FLAG = "-f";

    private final OSDao osDao;
    private final Lazy<Settings> settings;

    private String password;
    private List<Function<List<String>, List<String>>> behaviourChain = Arrays.asList(
            a -> checkAlreadySetup(a),
            a -> checkArgNum(a),
            a -> readPassword(a),
            a -> ensureSettingsPathExists(a),
            a -> setKeys(a),
            a -> setEncryptionProperties(a),
            a -> printSuccess()
    );

    @Inject
    public SetupMend(OSDao osDao, Lazy<Settings> settings) {
        this.osDao = osDao;
        this.settings = settings;
    }

    private List<String> checkAlreadySetup(List<String> args) {
        if (args.contains(FORCE_FLAG)) {
            args.remove(FORCE_FLAG);
        } else if (osDao.fileExists(new File(Config.CONFIG_PATH + Config.SETTINGS_FILE))) {
            System.err.println("MEND already has a Settings.xml file at " + Config.CONFIG_PATH + Config.SETTINGS_FILE);
            System.err.println("Please use the -f flag to overwrite it.");
            return null;
        }
        return args;
    }

    private List<String> checkArgNum(List<String> args) {
        if (args.size() != 2 && args.size() != 0) {
            System.err.println("Incorrect number of arguments!");
            System.err.println(getUsageText());
            return null;
        }
        return args;
    }

    private List<String> readPassword(List<String> args) {
        while (password == null) {
            char[] passArr1 = osDao.getConsole().readPassword("Please enter a password: ");
            String pass1 = new String(passArr1);
            char[] passArr2 = osDao.getConsole().readPassword("Please re-enter your password: ");
            String pass2 = new String(passArr2);
            if (pass1.equals(pass2)) {
                password = pass1;
            } else {
                System.err.println("Your passwords did not match. Please try again.");
            }
        }
        return args;
    }

    private List<String> ensureSettingsPathExists(List<String> args) {
        System.out.println("Creating Settings.xml in " + Config.CONFIG_PATH);
        new File(Config.CONFIG_PATH).mkdirs();
        return args;
    }

    private List<String> setKeys(List<String> args) {
        try {
            if (args.size() == 2) {
                setKeysFromInputFile(password, args.get(0), args.get(1));
            } else {
                setKeysGenerated(password);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return args;
    }

    private List<String> setEncryptionProperties(List<String> args) {
        //TODO probably move the preferred algo stuff out of Config
        settings.get().setValue(Settings.Name.PREFERREDAES, Config.PREFERRED_AES_ALG());
        settings.get().setValue(Settings.Name.PREFERREDRSA, Config.PREFERRED_RSA_ALG());
        settings.get().setValue(Settings.Name.AESKEYSIZE, Integer.toString(Config.AES_KEY_SIZE()));
        settings.get().setValue(Settings.Name.RSAKEYSIZE, Integer.toString(Config.RSA_KEY_SIZE()));
        return args;
    }

    private List<String> printSuccess() {
        System.out.println("MEND Successfully set up.");
        return null;
    }

    @Override
    public void execute(List<String> args) {
        executeBehaviourChain(behaviourChain, args);
    }

    private void setKeysFromInputFile(String password, String privateKeyFilePath, String publicKeyFilePath) throws
            Exception {
        //make sure the file exists
        File privateKeyFile = new File(privateKeyFilePath);
        if (!privateKeyFile.exists()) {
            System.err.println("Could not find file: " + privateKeyFilePath);
            return;
        }
        File publicKeyFile = new File(publicKeyFilePath);
        if (!publicKeyFile.exists()) {
            System.err.println("Could not find file: " + publicKeyFilePath);
            return;
        }

        FileInputStream privateKeyFileInputStream = null;
        try {
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

                setKeys(password, new KeyPair(publicKey, privateKey));
            } finally {
                if (publicKeyFileInputStream != null)
                    publicKeyFileInputStream.close();
            }
        } finally {
            if (privateKeyFileInputStream != null)
                privateKeyFileInputStream.close();
        }
    }

    private void setKeysGenerated(String password) throws Exception {
        //Generate an RSA key pair.
        KeyPairGenerator keyGen;
        keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(Config.RSA_KEY_SIZE());
        KeyPair keyPair = keyGen.genKeyPair();
        setKeys(password, keyPair);
    }

    private void setKeys(String password, KeyPair keyPair) throws Exception {
        //generate an aes key from the password
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), Config.PASSCHECK_SALT, Config.AES_KEY_GEN_ITERATIONS,
                Config.AES_KEY_SIZE());
        SecretKey tmp = factory.generateSecret(spec);
        SecretKey aesKey = new SecretKeySpec(tmp.getEncoded(), "AES");
        Cipher aesCipher = Cipher.getInstance(Config.PREFERRED_AES_ALG());
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, Config.STANDARD_IV);

        //Encrypt the private key with the password.
        byte[] encryptedPrivateKey = aesCipher.doFinal(keyPair.getPrivate().getEncoded());
        //encrypt the text
        byte[] cipherText = aesCipher.doFinal(Config.PASSCHECK_TEXT.getBytes("UTF-8"));

        //Write the encrypted private key to settings
        //TODO only commented to compile
        //SettingsImpl.instance().setValue(Config.Settings.PRIVATEKEY, Base64.encodeBase64URLSafeString(encryptedPrivateKey));
        //Add a public key element to the settings file containing the public key.
        //TODO only commented to compile
        //SettingsImpl.instance().setValue(Config.Settings.PUBLICKEY, Base64.encodeBase64URLSafeString(keyPair.getPublic().getEncoded()));
        //Add the encrypted pass check text to the Settings
        //TODO only commented to compile
        //SettingsImpl.instance().setValue(Config.Settings.PASSCHECK, Base64.encodeBase64URLSafeString(cipherText));
    }

    @Override
    public String getUsageText() {
        return "Usage:\tmend setup [<path_to_private_key_file> <path_to_public_key_file>]";
    }

    @Override
    public String getDescriptionText() {
        return "Run this command first. It creates some basic config necessary.";
    }

    @Override
    protected List<String> getCommandAliases() {
        return Arrays.asList(COMMAND_NAME);
    }

}
