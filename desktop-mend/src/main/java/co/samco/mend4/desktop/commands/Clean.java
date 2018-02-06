package co.samco.mend4.desktop.commands;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.impl.SettingsImpl.CorruptSettingsException;
import co.samco.mend4.core.impl.SettingsImpl.InvalidSettingNameException;
import co.samco.mend4.desktop.dao.OSDao;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import dagger.Lazy;

import javax.inject.Inject;

public class Clean extends Command {

    private final String COMMAND_NAME = "clean";
    private final Lazy<Settings> settings;
    private final OSDao OSDao;
    private final PrintStreamProvider log;

    @Inject
    public Clean(PrintStreamProvider log, Lazy<Settings> settings, OSDao OSDao) {
        this.settings = settings;
        this.OSDao = OSDao;
        this.log = log;
    }

    private void assertDecDirNotNull(String decDir) throws CorruptSettingsException {
        if (decDir == null) {
            throw new CorruptSettingsException("You need to set the "
                    + Config.SETTINGS_NAMES_MAP.get(Config.Settings.DECDIR.ordinal())
                    + " property before you can clean the files in it.");
        }
    }

    private void assertShredCommandNotNull(String shredCommand) throws CorruptSettingsException {
        if (shredCommand == null) {
            throw new CorruptSettingsException("You need to set the "
                    + Config.SETTINGS_NAMES_MAP.get(Config.Settings.SHREDCOMMAND.ordinal())
                    + " property in your settings before you can shred files.");
        }
    }

    private void shredFile(File file, String shredCommand) throws IOException, InterruptedException {
        String absolutePath = OSDao.getAbsolutePath(file);
        log.err().println("Cleaning: " + absolutePath);
        String[] shredCommandArgs = generateShredCommandArgs(absolutePath, shredCommand);
        Process tr = OSDao.executeCommand(shredCommandArgs);
        tr.waitFor();
    }

    @Override
    public void execute(List<String> args) {
        try {
            String decDir = settings.get().getValue(Config.Settings.DECDIR);
            assertDecDirNotNull(decDir);
            String shredCommand = settings.get().getValue(Config.Settings.SHREDCOMMAND);
            assertShredCommandNotNull(shredCommand);

            File[] directoryListing = OSDao.getDirectoryListing(new File(decDir));
            for (File child : directoryListing) {
                shredFile(child, shredCommand);
            }
            log.err().println("Cleaning Complete");
        } catch (CorruptSettingsException | InvalidSettingNameException | IOException | InterruptedException e) {
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
