package co.samco.mend4.desktop.commands;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.impl.SettingsImpl.CorruptSettingsException;
import co.samco.mend4.core.impl.SettingsImpl.InvalidSettingNameException;
import co.samco.mend4.desktop.dao.OSDao;
import co.samco.mend4.desktop.helper.ShredHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import dagger.Lazy;

import javax.inject.Inject;

public class Clean extends Command {

    private final String COMMAND_NAME = "clean";
    private final Lazy<Settings> settings;
    private final PrintStreamProvider log;
    private final ShredHelper shredHelper;

    @Inject
    public Clean(PrintStreamProvider log, Lazy<Settings> settings, ShredHelper shredHelper) {
        this.settings = settings;
        this.log = log;
        this.shredHelper = shredHelper;
    }

    private String getDecDir() throws CorruptSettingsException, InvalidSettingNameException {
        String decDir = settings.get().getValue(Config.Settings.DECDIR);
        if (decDir == null) {
            throw new CorruptSettingsException("You need to set the "
                    + Config.SETTINGS_NAMES_MAP.get(Config.Settings.DECDIR.ordinal())
                    + " property before you can clean the files in it.");
        }
        return decDir;
    }

    @Override
    public void execute(List<String> args) {
        try {
            String decDir = getDecDir();
            shredHelper.shredFilesInDirectory(decDir);
            log.err().println("Cleaning Complete");
        } catch (CorruptSettingsException | InvalidSettingNameException | IOException e) {
            log.err().println(e.getMessage());
        }
    }

    @Override
    public String getUsageText() {
        return "Usage:\tmend clean";
    }

    @Override
    public String getDescriptionText() {
        return "Runs the " + Config.SETTINGS_NAMES_MAP.get(Config.Settings.SHREDCOMMAND.ordinal()) + " on every file " +
                "in your decrypt directory.";
    }

    @Override
    protected List<String> getCommandAliases() {
        return Arrays.asList(COMMAND_NAME);
    }

}
