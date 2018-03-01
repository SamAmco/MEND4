package co.samco.mend4.desktop.commands;

import co.samco.mend4.core.AppProperties;
import co.samco.mend4.core.crypto.CryptoProvider;
import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.core.OSDao;
import co.samco.mend4.core.Settings;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.helper.CryptoHelper;
import co.samco.mend4.desktop.helper.FileResolveHelper;
import co.samco.mend4.desktop.helper.ShredHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import org.apache.commons.codec.binary.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class Unlock extends Command {
    public static final String COMMAND_NAME = "unlock";

    private final I18N strings;
    private final OSDao osDao;
    private final Settings settings;
    private final PrintStreamProvider log;
    private final CryptoHelper cryptoHelper;
    private final CryptoProvider cryptoProvider;
    private final ShredHelper shredHelper;

    char[] password;

    private final File privateKeyFile;
    private final File publicKeyFile;
    private List<Function<List<String>, List<String>>> behaviourChain = Arrays.asList(
            a -> readPassword(a),
            a -> checkPassword(a),
            a -> shredExistingKeys(a),
            a -> decryptAndWriteKeys(a)
    );

    @Inject
    public Unlock(I18N strings, OSDao osDao, Settings settings, PrintStreamProvider log, CryptoHelper cryptoHelper,
                  CryptoProvider cryptoProvider, ShredHelper shredHelper, FileResolveHelper fileResolveHelper) {
        this.strings = strings;
        this.osDao = osDao;
        this.settings = settings;
        this.log = log;
        this.cryptoHelper = cryptoHelper;
        this.cryptoProvider = cryptoProvider;
        this.shredHelper = shredHelper;
        privateKeyFile = new File(fileResolveHelper.getPrivateKeyPath());
        publicKeyFile = new File(fileResolveHelper.getPublicKeyPath());
    }

    private List<String> readPassword(List<String> args) {
        password = osDao.readPassword(strings.get("Unlock.enterPassword"));
        return args;
    }

    private List<String> checkPassword(List<String> args) {
        try {
            if (cryptoProvider.checkPassword(password, AppProperties.PASSCHECK_TEXT,
                    settings.getValue(Settings.Name.PASSCHECK))) {
                log.err().println(strings.get("Unlock.incorrectPassword"));
                return null;
            }
        }
        catch (IOException | InvalidKeySpecException | NoSuchAlgorithmException | InvalidKeyException | InvalidAlgorithmParameterException
                | NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException | CorruptSettingsException e) {
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
        catch (IOException | CorruptSettingsException e) {
            log.err().println(e.getMessage());
            return null;
        }
        return args;
    }

    private List<String> decryptAndWriteKeys(List<String> args) {
        try {
            byte[] privateKey = cryptoProvider.decryptEncodedKey(password, settings.getValue(Settings.Name.PRIVATEKEY));
            byte[] publicKey = Base64.decodeBase64(settings.getValue(Settings.Name.PUBLICKEY));
            osDao.writeDataToFile(privateKey, privateKeyFile);
            osDao.writeDataToFile(publicKey, publicKeyFile);
            log.out().println(strings.get("Unlock.unlocked"));
        } catch (CorruptSettingsException | NoSuchAlgorithmException | InvalidKeyException
                | InvalidAlgorithmParameterException | NoSuchPaddingException | InvalidKeySpecException
                | IOException | BadPaddingException | IllegalBlockSizeException e) {
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
