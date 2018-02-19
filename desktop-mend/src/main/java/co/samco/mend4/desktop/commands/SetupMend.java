package co.samco.mend4.desktop.commands;

import java.io.*;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.inject.Inject;
import javax.xml.transform.TransformerException;

import co.samco.mend4.core.Config;

import co.samco.mend4.core.Settings;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.dao.OSDao;
import co.samco.mend4.desktop.helper.CryptoHelper;
import co.samco.mend4.desktop.helper.FileResolveHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import dagger.Lazy;

public class SetupMend extends Command {
    public static final String COMMAND_NAME = "setup";
    public static final String FORCE_FLAG = "-f";

    private final PrintStreamProvider log;
    private final I18N strings;
    private final OSDao osDao;
    private final CryptoHelper cryptoHelper;
    private final FileResolveHelper fileResolveHelper;
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
    public SetupMend(PrintStreamProvider log, I18N strings, OSDao osDao, CryptoHelper cryptoHelper,
                     FileResolveHelper fileResolveHelper, Lazy<Settings> settings) {
        this.log = log;
        this.strings = strings;
        this.osDao = osDao;
        this.cryptoHelper = cryptoHelper;
        this.fileResolveHelper = fileResolveHelper;
        this.settings = settings;
    }

    private List<String> checkAlreadySetup(List<String> args) {
        if (args.contains(FORCE_FLAG)) {
            args.remove(FORCE_FLAG);
        } else if (osDao.fileExists(new File(Config.CONFIG_PATH + Config.SETTINGS_FILE))) {
            log.err().println(strings.getf("SetupMend.alreadySetup",
                    Config.CONFIG_PATH + Config.SETTINGS_FILE, FORCE_FLAG));
            return null;
        }
        return args;
    }

    private List<String> checkArgNum(List<String> args) {
        if (args.size() != 2 && args.size() != 0) {
            log.err().println(strings.getf("General.invalidArgNum", COMMAND_NAME));
            return null;
        }
        return args;
    }

    private List<String> readPassword(List<String> args) {
        while (password == null) {
            char[] passArr1 = osDao.getConsole().readPassword(strings.get("SetupMend.enterPassword"));
            String pass1 = new String(passArr1);
            char[] passArr2 = osDao.getConsole().readPassword(strings.get("SetupMend.reEnterPassword"));
            String pass2 = new String(passArr2);
            if (pass1.equals(pass2)) {
                password = pass1;
            } else {
                log.err().println(strings.get("SetupMend.passwordMismatch"));
            }
        }
        return args;
    }

    private List<String> ensureSettingsPathExists(List<String> args) {
        log.out().println("Creating Settings.xml in " + Config.CONFIG_PATH);
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
        } catch (InvalidKeyException | InvalidAlgorithmParameterException | NoSuchAlgorithmException
                | TransformerException | Settings.CorruptSettingsException | InvalidKeySpecException
                | IllegalBlockSizeException | Settings.InvalidSettingNameException | BadPaddingException
                | NoSuchPaddingException | IOException e) {
            e.printStackTrace();
        }
        return args;
    }

    private List<String> setEncryptionProperties(List<String> args) {
        //TODO probably move the preferred algo stuff out of Config
        try {
            settings.get().setValue(Settings.Name.PREFERREDAES, Config.PREFERRED_AES_ALG);
            settings.get().setValue(Settings.Name.PREFERREDRSA, Config.PREFERRED_RSA_ALG);
            settings.get().setValue(Settings.Name.AESKEYSIZE, Integer.toString(Config.AES_KEY_SIZE));
            settings.get().setValue(Settings.Name.RSAKEYSIZE, Integer.toString(Config.RSA_KEY_SIZE));
        } catch (TransformerException | Settings.CorruptSettingsException | Settings.InvalidSettingNameException e) {
            e.printStackTrace();
        }
        return args;
    }

    private List<String> printSuccess() {
        log.out().println("MEND Successfully set up.");
        return null;
    }

    @Override
    public void execute(List<String> args) {
        executeBehaviourChain(behaviourChain, args);
    }

    private void setKeysFromInputFile(String password, String privateKeyFilePath, String publicKeyFilePath) throws
            NoSuchAlgorithmException, IOException, InvalidKeySpecException, NoSuchPaddingException,
            InvalidAlgorithmParameterException, TransformerException, IllegalBlockSizeException,
            Settings.CorruptSettingsException, BadPaddingException, Settings.InvalidSettingNameException,
            InvalidKeyException {
        File privateKeyFile = fileResolveHelper.resolveFile(privateKeyFilePath);
        File publicKeyFile = fileResolveHelper.resolveFile(publicKeyFilePath);
        setKeys(password, cryptoHelper.readKeyPairFromFiles(privateKeyFile, publicKeyFile));
    }

    private void setKeysGenerated(String password) throws NoSuchAlgorithmException, TransformerException,
            InvalidKeyException, InvalidAlgorithmParameterException, NoSuchPaddingException, BadPaddingException,
            Settings.InvalidSettingNameException, UnsupportedEncodingException, Settings.CorruptSettingsException,
            InvalidKeySpecException, IllegalBlockSizeException {
        setKeys(password, cryptoHelper.generateKeyPair());
    }

    private void setKeys(String password, KeyPair keyPair) throws NoSuchPaddingException,
            UnsupportedEncodingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException,
            BadPaddingException, InvalidAlgorithmParameterException, InvalidKeySpecException,
            Settings.InvalidSettingNameException, TransformerException, Settings.CorruptSettingsException {
        CryptoHelper.EncodedKeyInfo keyInfo = cryptoHelper.getEncodedKeyInfo(password, keyPair);
        settings.get().setValue(Settings.Name.PRIVATEKEY, keyInfo.getPrivateKey());
        settings.get().setValue(Settings.Name.PUBLICKEY, keyInfo.getPublicKey());
        settings.get().setValue(Settings.Name.PASSCHECK, keyInfo.getCipherText());
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
