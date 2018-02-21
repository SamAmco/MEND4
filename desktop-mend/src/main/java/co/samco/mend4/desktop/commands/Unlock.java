package co.samco.mend4.desktop.commands;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.inject.Inject;

import co.samco.mend4.core.Settings;
import co.samco.mend4.core.Settings.CorruptSettingsException;
import co.samco.mend4.core.Settings.InvalidSettingNameException;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.dao.OSDao;
import co.samco.mend4.desktop.helper.CryptoHelper;
import co.samco.mend4.desktop.helper.FileResolveHelper;
import co.samco.mend4.desktop.helper.ShredHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import dagger.Lazy;
import org.apache.commons.codec.binary.Base64;

import co.samco.mend4.core.AppProperties;

public class Unlock extends Command {
    public static final String COMMAND_NAME = "unlock";

    private final I18N strings;
    private final OSDao osDao;
    private final Lazy<Settings> settings;
    private final PrintStreamProvider log;
    private final CryptoHelper cryptoHelper;
    private final ShredHelper shredHelper;
    private final FileResolveHelper fileResolveHelper;

    char[] password;

    private final File privateKeyFile;// = new File(Config.CONFIG_DIR_NAME + Config.PRIVATE_KEY_FILE_NAME);
    private final File publicKeyFile;// = new File(Config.CONFIG_DIR_NAME + Config.PUBLIC_KEY_FILE_NAME);
    private List<Function<List<String>, List<String>>> behaviourChain = Arrays.asList(
            a -> readPassword(a),
            a -> checkPassword(a),
            a -> shredExistingKeys(a),
            a -> decryptAndWriteKeys(a)
    );

    @Inject
    public Unlock(I18N strings, OSDao osDao, Lazy<Settings> settings, PrintStreamProvider log,
                  CryptoHelper cryptoHelper, ShredHelper shredHelper, FileResolveHelper fileResolveHelper) {
        this.strings = strings;
        this.osDao = osDao;
        this.settings = settings;
        this.log = log;
        this.cryptoHelper = cryptoHelper;
        this.shredHelper = shredHelper;
        this.fileResolveHelper = fileResolveHelper;
        privateKeyFile = new File(fileResolveHelper.getPrivateKeyPath());
        publicKeyFile = new File(fileResolveHelper.getPublicKeyPath());
    }

    private List<String> readPassword(List<String> args) {
        password = osDao.readPassword(strings.get("Unlock.enterPassword"));
        return args;
    }

    private List<String> checkPassword(List<String> args) {
        try {
            byte[] cipherText = Base64.decodeBase64(settings.get().getValue(Settings.Name.PASSCHECK));
            byte[] plainText = cryptoHelper.decryptBytesWithPassword(cipherText, password);

            if (!AppProperties.PASSCHECK_TEXT.equals(new String(plainText, "UTF-8"))) {
                log.err().println(strings.get("Unlock.incorrectPassword"));
                return null;
            }
        }
        catch (CorruptSettingsException | InvalidSettingNameException | InvalidKeySpecException
                | NoSuchAlgorithmException | InvalidKeyException | InvalidAlgorithmParameterException
                | NoSuchPaddingException | UnsupportedEncodingException | BadPaddingException
                | IllegalBlockSizeException e) {
            log.err().println(e.getMessage());
        }
        return args;
    }

    private List<String> shredExistingKeys(List<String> args) {
        try {
            if(osDao.fileExists(privateKeyFile)) {
                shredHelper.tryShredFile(privateKeyFile.getPath());
            }
            if (osDao.fileExists(publicKeyFile)) {
                shredHelper.tryShredFile(publicKeyFile.getPath());
            }
        }
        catch (IOException | InvalidSettingNameException | CorruptSettingsException e) {
            log.err().println(e.getMessage());
            return null;
        }
        return args;
    }

    private List<String> decryptAndWriteKeys(List<String> args) {
        try {
            byte[] encryptedPrivateKey = Base64.decodeBase64(settings.get().getValue(Settings.Name.PRIVATEKEY));
            byte[] privateKey = cryptoHelper.decryptBytesWithPassword(encryptedPrivateKey, password);
            byte[] publicKey = Base64.decodeBase64(settings.get().getValue(Settings.Name.PUBLICKEY));
            osDao.writeDataToFile(privateKey, privateKeyFile);
            osDao.writeDataToFile(publicKey, publicKeyFile);
            log.out().println(strings.get("Unlock.unlocked"));
        } catch (CorruptSettingsException | InvalidSettingNameException | NoSuchAlgorithmException
                | InvalidKeyException | InvalidAlgorithmParameterException | NoSuchPaddingException
                | InvalidKeySpecException | IOException | BadPaddingException | IllegalBlockSizeException e) {
            log.err().println(e.getMessage());
            return null;
        }
        return args;
    }

    @Override
    public void execute(List<String> args) {
        executeBehaviourChain(behaviourChain, args);
    }

    @Override
    public String getUsageText() {
        return strings.getf("Unlock.usage", COMMAND_NAME);
    }

    @Override
    public String getDescriptionText() {
        return strings.get("Unlock.description");
    }

    @Override
    protected List<String> getCommandAliases() {
        return Arrays.asList(COMMAND_NAME);
    }

}
