package co.samco.mend4.desktop.commands;

import co.samco.mend4.core.CorruptSettingsException;
import co.samco.mend4.core.Settings;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.helper.ShredHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class Clean extends Command {
    public static final String COMMAND_NAME = "clean";

    private final PrintStreamProvider log;
    private final ShredHelper shredHelper;
    private final Settings settings;
    private final I18N strings;

    @Inject
    public Clean(I18N strings, PrintStreamProvider log, ShredHelper shredHelper, Settings settings) {
        this.strings = strings;
        this.log = log;
        this.shredHelper = shredHelper;
        this.settings = settings;
    }

    @Override
    public void execute(List<String> args) {
        try {
            String decDir = settings.getValue(Settings.Name.DECDIR);
            shredHelper.shredFilesInDirectory(decDir);
            log.err().println(strings.get("Clean.cleanComplete"));
        } catch (CorruptSettingsException | IOException e) {
            log.err().println(e.getMessage());
        }
    }

    @Override
    public String getUsageText() {
        return strings.getf("Clean.usage", COMMAND_NAME);
    }

    @Override
    public String getDescriptionText() {
        return strings.getf("Clean.description", Settings.Name.SHREDCOMMAND.toString());
    }

    @Override
    protected List<String> getCommandAliases() {
        return Arrays.asList(COMMAND_NAME);
    }

}
