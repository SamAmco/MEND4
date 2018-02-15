package co.samco.mend4.desktop.commands;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import co.samco.mend4.core.Settings;
import co.samco.mend4.core.Settings.CorruptSettingsException;
import co.samco.mend4.core.Settings.InvalidSettingNameException;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.helper.FileResolveHelper;
import co.samco.mend4.desktop.helper.ShredHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;

import javax.inject.Inject;

public class Clean extends Command {
    public static final String COMMAND_NAME = "clean";

    private final PrintStreamProvider log;
    private final ShredHelper shredHelper;
    private final FileResolveHelper fileResolveHelper;
    private final I18N strings;

    @Inject
    public Clean(I18N strings, PrintStreamProvider log, ShredHelper shredHelper,
                 FileResolveHelper fileResolveHelper) {
        this.strings = strings;
        this.log = log;
        this.shredHelper = shredHelper;
        this.fileResolveHelper = fileResolveHelper;
    }

    @Override
    public void execute(List<String> args) {
        try {
            String decDir = fileResolveHelper.getDecDir();
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
        return strings.getf("Clean.description", Settings.Name.SHREDCOMMAND.toString());
    }

    @Override
    protected List<String> getCommandAliases() {
        return Arrays.asList(COMMAND_NAME);
    }

}
