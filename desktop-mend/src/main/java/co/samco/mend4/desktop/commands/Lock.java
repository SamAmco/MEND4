package co.samco.mend4.desktop.commands;

import co.samco.mend4.core.Settings;
import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.core.OSDao;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.exception.SettingRequiredException;
import co.samco.mend4.desktop.helper.FileResolveHelper;
import co.samco.mend4.desktop.helper.SettingsHelper;
import co.samco.mend4.desktop.helper.ShredHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class Lock extends Command {
    private final String COMMAND_NAME = "lock";
    private final OSDao osDao;
    private final PrintStreamProvider log;
    private final SettingsHelper settingsHelper;
    private final ShredHelper shredHelper;
    private final I18N strings;
    private final FileResolveHelper fileResolveHelper;

    private final List<Function<List<String>, List<String>>> behaviourChain = Arrays.asList(
            a -> assertSettingsPresent(a),
            a -> performLock(a)
    );

    @Inject
    public Lock(I18N strings, PrintStreamProvider log, OSDao OSDao, SettingsHelper settingsHelper,
                ShredHelper shredHelper, FileResolveHelper fileResolveHelper) {
        this.osDao = OSDao;
        this.log = log;
        this.settingsHelper = settingsHelper;
        this.shredHelper = shredHelper;
        this.strings = strings;
        this.fileResolveHelper = fileResolveHelper;
    }

    private void printIfNotNull(String message) {
        if (message != null) {
            log.err().println(message);
        }
    }

    private void printLockStatus(String messageIfUnlocked, String messageIfLocked) {
        File privateKeyFile = new File(fileResolveHelper.getPrivateKeyPath());
        boolean locked = !osDao.fileExists(privateKeyFile);
        printIfNotNull(locked ? messageIfLocked : messageIfUnlocked);
    }

    private List<String> assertSettingsPresent(List<String> args) {
        try {
            settingsHelper.assertRequiredSettingsExist(new Settings.Name[]{Settings.Name.SHREDCOMMAND}, COMMAND_NAME);
        } catch (IOException | SettingRequiredException e) {
            failWithMessage(log, e.getMessage());
            return null;
        }
        return args;
    }

    private List<String> performLock(List<String> args) {
        try {
            printLockStatus(null, strings.get("Lock.notUnlocked"));
            shredHelper.tryShredFile(fileResolveHelper.getPrivateKeyPath());
            shredHelper.tryShredFile(fileResolveHelper.getPublicKeyPath());
            printLockStatus(strings.get("Lock.lockFailed"), strings.get("Lock.locked"));
        } catch (IOException | CorruptSettingsException e) {
            failWithMessage(log, e.getMessage());
        }
        return null;
    }

    @Override
    public void execute(List<String> args) {
        executeBehaviourChain(behaviourChain, args);
    }

    @Override
    public String getUsageText() {
        return strings.getf("Lock.usage", COMMAND_NAME);
    }

    @Override
    public String getDescriptionText() {
        return strings.get("Lock.description");
    }

    @Override
    protected List<String> getCommandAliases() {
        return Arrays.asList(COMMAND_NAME);
    }
}
