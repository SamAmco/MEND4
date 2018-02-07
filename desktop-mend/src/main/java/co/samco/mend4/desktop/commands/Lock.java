package co.samco.mend4.desktop.commands;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.impl.SettingsImpl;
import co.samco.mend4.desktop.dao.OSDao;
import co.samco.mend4.desktop.helper.ShredHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;

import javax.inject.Inject;

public class Lock extends Command {
    private final String COMMAND_NAME = "lock";
    private final OSDao osDao;
    private final PrintStreamProvider log;
    private final ShredHelper shredHelper;

    @Inject
    public Lock(PrintStreamProvider log, OSDao OSDao, ShredHelper shredHelper) {
        this.osDao = OSDao;
        this.log = log;
        this.shredHelper = shredHelper;
    }

    private void printIfNotNull(String message) {
        if (message != null) {
            log.err().println(message);
        }
    }

    private void printLockStatus(String messageIfUnlocked, String messageIfLocked) {
        File privateKeyFile = new File(Config.CONFIG_PATH + Config.PRIVATE_KEY_FILE_DEC);
        boolean locked = !osDao.fileExists(privateKeyFile);
        printIfNotNull(locked ? messageIfLocked : messageIfUnlocked);
    }

    @Override
    public void execute(List<String> args) {
        try {
            printLockStatus(null, "MEND did not appear to be unlocked.");
            shredHelper.tryShredFile(Config.CONFIG_PATH + Config.PRIVATE_KEY_FILE_DEC);
            shredHelper.tryShredFile(Config.CONFIG_PATH + Config.PUBLIC_KEY_FILE);
            printLockStatus("Locking may have failed, your private key file still exists.",
                    "MEND Locked.");
        } catch (IOException | SettingsImpl.CorruptSettingsException | SettingsImpl.InvalidSettingNameException e) {
            log.err().println(e.getMessage());
        }
    }

    @Override
    public String getUsageText() {
        return "Usage:\tmend lock";
    }

    @Override
    public String getDescriptionText() {
        return "Shreds the decrypted private key. Requires shred to be installed.";
    }

    @Override
    protected List<String> getCommandAliases() {
        return Arrays.asList(COMMAND_NAME);
    }
}
