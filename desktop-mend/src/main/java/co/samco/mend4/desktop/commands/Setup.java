package co.samco.mend4.desktop.commands;

import co.samco.mend4.core.AppProperties;
import co.samco.mend4.core.OSDao;
import co.samco.mend4.core.Settings;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.helper.CryptoHelper;
import co.samco.mend4.desktop.helper.FileResolveHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.inject.Inject;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

//TODO allow people to call setup with different flags to modify things like key sizes etc
public class Setup extends Command {
    public static final String COMMAND_NAME = "setup";
    public static final String FORCE_FLAG = "-f";

    private final PrintStreamProvider log;
    private final I18N strings;
    private final OSDao osDao;
    private final CryptoHelper cryptoHelper;
    private final FileResolveHelper fileResolveHelper;
    private final Settings settings;

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
    public Setup(PrintStreamProvider log, I18N strings, OSDao osDao, CryptoHelper cryptoHelper,
                 FileResolveHelper fileResolveHelper, Settings settings) {
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
        } else if (osDao.fileExists(new File(fileResolveHelper.getSettingsPath()))) {
            log.err().println(strings.getf("SetupMend.alreadySetup",
                    fileResolveHelper.getSettingsPath(), FORCE_FLAG));
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
            char[] passArr1 = osDao.readPassword(strings.get("SetupMend.enterPassword"));
            String pass1 = new String(passArr1);
            char[] passArr2 = osDao.readPassword(strings.get("SetupMend.reEnterPassword"));
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
        log.out().println(strings.getf("SetupMend.creating", fileResolveHelper.getSettingsPath()));
        osDao.mkdirs(new File(fileResolveHelper.getSettingsPath()));
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
                | TransformerException | InvalidKeySpecException
                | IllegalBlockSizeException | BadPaddingException
                | NoSuchPaddingException | IOException e) {
            log.err().println(e.getMessage());
            return null;
        }
        return args;
    }

    private List<String> setEncryptionProperties(List<String> args) {
        try {
            settings.setValue(Settings.Name.PREFERREDAES, AppProperties.PREFERRED_AES_ALG);
            settings.setValue(Settings.Name.PREFERREDRSA, AppProperties.PREFERRED_RSA_ALG);
            settings.setValue(Settings.Name.AESKEYSIZE, Integer.toString(AppProperties.PREFERRED_AES_KEY_SIZE));
            settings.setValue(Settings.Name.RSAKEYSIZE, Integer.toString(AppProperties.PREFERRED_RSA_KEY_SIZE));
        } catch (IOException  e) {
            log.err().println(e.getMessage());
            return null;
        }
        return args;
    }

    private List<String> printSuccess() {
        log.err().println(strings.get("SetupMend.complete"));
        return null;
    }

    @Override
    public void execute(List<String> args) {
        executeBehaviourChain(behaviourChain, args);
    }

    private void setKeysFromInputFile(String password, String privateKeyFilePath, String publicKeyFilePath) throws
            NoSuchAlgorithmException, IOException, InvalidKeySpecException, NoSuchPaddingException,
            InvalidAlgorithmParameterException, TransformerException, IllegalBlockSizeException, BadPaddingException,
            InvalidKeyException {
        File privateKeyFile = fileResolveHelper.resolveFile(privateKeyFilePath);
        File publicKeyFile = fileResolveHelper.resolveFile(publicKeyFilePath);
        setKeys(password, cryptoHelper.readKeyPairFromFiles(privateKeyFile, publicKeyFile));
    }

    private void setKeysGenerated(String password) throws NoSuchAlgorithmException, TransformerException,
            InvalidKeyException, InvalidAlgorithmParameterException, NoSuchPaddingException, BadPaddingException,
            IOException, InvalidKeySpecException, IllegalBlockSizeException {
        setKeys(password, cryptoHelper.generateKeyPair());
    }

    private void setKeys(String password, KeyPair keyPair) throws NoSuchPaddingException,
            IOException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException,
            BadPaddingException, InvalidAlgorithmParameterException, InvalidKeySpecException, TransformerException {
        CryptoHelper.EncodedKeyInfo keyInfo = cryptoHelper.getEncodedKeyInfo(password, keyPair);
        settings.setValue(Settings.Name.PRIVATEKEY, keyInfo.getPrivateKey());
        settings.setValue(Settings.Name.PUBLICKEY, keyInfo.getPublicKey());
        settings.setValue(Settings.Name.PASSCHECK, keyInfo.getCipherText());
    }

    @Override
    public String getUsageText() {
        return strings.getf("SetupMend.usage", COMMAND_NAME);
    }

    @Override
    public String getDescriptionText() {
        return strings.get("SetupMend.description");
    }

    @Override
    protected List<String> getCommandAliases() {
        return Arrays.asList(COMMAND_NAME);
    }

}
