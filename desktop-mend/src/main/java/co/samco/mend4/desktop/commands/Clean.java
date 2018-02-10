package co.samco.mend4.desktop.commands;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.impl.SettingsImpl.CorruptSettingsException;
import co.samco.mend4.core.impl.SettingsImpl.InvalidSettingNameException;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.helper.ShredHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import dagger.Lazy;

import javax.inject.Inject;

public class Clean extends Command {
    public static final String COMMAND_NAME = "clean";

    private final Lazy<Settings> settings;
    private final PrintStreamProvider log;
    private final ShredHelper shredHelper;
    private final I18N strings;

    @Inject
    public Clean(I18N strings, PrintStreamProvider log, Lazy<Settings> settings, ShredHelper shredHelper) {
        this.strings = strings;
        this.settings = settings;
        this.log = log;
        this.shredHelper = shredHelper;
    }

    private String getDecDir() throws CorruptSettingsException, InvalidSettingNameException {
        String decDir = settings.get().getValue(Config.Settings.DECDIR);
        if (decDir == null) {
            throw new CorruptSettingsException(strings.getf("Clean.noDecDir",
                    Config.SETTINGS_NAMES_MAP.get(Config.Settings.DECDIR.ordinal())));
        }
        return decDir;
    }

    @Override
    public void execute(List<String> args) {
        try {
            String decDir = getDecDir();
            shredHelper.shredFilesInDirectory(decDir);
            log.err().println(strings.get("Clean.cleanComplete"));
        } catch (CorruptSettingsException | InvalidSettingNameException | IOException e) {
            log.err().println(e.getMessage());
        }
    }

    @Override
    public String getUsageText() {
        return strings.getf("Clean.usage", COMMAND_NAME);
    }

    @Override
    public String getDescriptionText() {
        return strings.getf("Clean.description", Config.SETTINGS_NAMES_MAP.get(Config.Settings.SHREDCOMMAND.ordinal()));
    }

    @Override
    protected List<String> getCommandAliases() {
        return Arrays.asList(COMMAND_NAME);
    }

}
