package co.samco.mend4.desktop.commands;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.Settings.CorruptSettingsException;
import co.samco.mend4.core.Settings.InvalidSettingNameException;
import co.samco.mend4.core.Settings.UnInitializedSettingsException;

import javax.inject.Inject;

public class Clean extends Command {

    private final String COMMAND_NAME = "clean";

    @Inject
    public Clean() {}

    @Override
    public void execute(List<String> args) {
        try {
            String decDir = Settings.instance().getValue(Config.Settings.DECDIR);
            if (decDir == null) {
                System.err.println("You need to set the "
                        + Config.SETTINGS_NAMES_MAP.get(Config.Settings.DECDIR.ordinal()) + " property before you can" +
                        " clean the files in it.");
                return;
            }
            File dir = new File(decDir);
            File[] directoryListing = dir.listFiles();
            if (directoryListing == null) {
                System.err.println("Could not find the directory: " + dir.getAbsolutePath());
                return;
            }
            for (File child : directoryListing) {
                System.err.println("Cleaning: " + child.getAbsolutePath());
                String shredCommand = Settings.instance().getValue(Config.Settings.SHREDCOMMAND);
                if (shredCommand == null) {
                    System.err.println("You need to set the " + Config.SETTINGS_NAMES_MAP.get(Config.Settings
                            .SHREDCOMMAND.ordinal())
                            + " property in your settings before you can shred files.");
                    return;
                }
                String[] shredCommandArgs = generateShredCommandArgs(child.getAbsolutePath(), shredCommand);
                Process tr = Runtime.getRuntime().exec(shredCommandArgs);
                tr.waitFor();
            }
            System.err.println("Cleaning Complete");
        } catch (CorruptSettingsException | InvalidSettingNameException | IOException | InterruptedException |
                UnInitializedSettingsException e) {
            System.err.println(e.getMessage());
        }
    }

    @Override
    public String getUsageText() {
        return "Usage:\tmend clean";
    }

    @Override
    public String getDescriptionText() {
        //return "Runs the " + Config.SETTINGS_NAMES_MAP.get(Config.Settings.SHREDCOMMAND.ordinal()) + " on every file " +
                //"in your decrypt directory.";
        return null;
    }

    @Override
    protected List<String> getCommandAliases() {
        return Arrays.asList(COMMAND_NAME);
    }

}
