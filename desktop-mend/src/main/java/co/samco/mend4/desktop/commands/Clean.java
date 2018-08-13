package co.samco.mend4.desktop.commands;

import co.samco.mend4.core.Settings;
import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.exception.SettingRequiredException;
import co.samco.mend4.desktop.helper.SettingsHelper;
import co.samco.mend4.desktop.helper.ShredHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class Clean extends Command {
    public static final String COMMAND_NAME = "clean";

    private final PrintStreamProvider log;
    private final SettingsHelper settingsHelper;
    private final ShredHelper shredHelper;
    private final Settings settings;
    private final I18N strings;

    private final List<Function<List<String>, List<String>>> behaviourChain = Arrays.asList(
            a -> assertSettingsPresent(a),
            a -> performClean(a)
    );

    @Inject
    public Clean(I18N strings, PrintStreamProvider log, SettingsHelper settingsHelper, ShredHelper shredHelper,
                 Settings settings) {
        this.strings = strings;
        this.log = log;
        this.settingsHelper = settingsHelper;
        this.shredHelper = shredHelper;
        this.settings = settings;
    }

    protected List<String> assertSettingsPresent(List<String> args) {
        try {
            settingsHelper.assertRequiredSettingsExist(new Settings.Name[]{
                            Settings.Name.DECDIR, Settings.Name.SHREDCOMMAND},
                    COMMAND_NAME);
        } catch (IOException | SettingRequiredException e) {
            failWithMessage(log, e.getMessage());
            return null;
        }
        return args;
    }

    public List<String> performClean(List<String> args) {
        try {
            String decDir = settings.getValue(Settings.Name.DECDIR);
            shredHelper.shredFilesInDirectory(decDir);
            log.err().println(strings.get("Clean.cleanComplete"));
        } catch (CorruptSettingsException | IOException e) {
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
