package co.samco.mend4.desktop.commands;

import java.util.*;
import java.util.function.Function;

import javax.inject.Inject;
import javax.xml.transform.TransformerException;

import co.samco.mend4.core.Settings;
import co.samco.mend4.core.Settings.CorruptSettingsException;
import co.samco.mend4.core.Settings.InvalidSettingNameException;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.helper.SettingsHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import dagger.Lazy;

public class SetProperty extends Command {
    public static final String COMMAND_NAME = "set";

    private final PrintStreamProvider log;
    private final I18N strings;
    private final Lazy<Settings> settings;
    private final SettingsHelper settingsHelper;

    private String propertyName;
    private String value;

    private final List<Function<List<String>, List<String>>> behaviourChain = Arrays.asList(
            a -> assertCorrectNumArgs(a),
            a -> assertPropertyExists(a),
            a -> setProperty(a)
    );

    @Inject
    public SetProperty(PrintStreamProvider log, I18N strings, Lazy<Settings> settings,
                       SettingsHelper settingsHelper) {
        this.log = log;
        this.strings = strings;
        this.settings = settings;
        this.settingsHelper = settingsHelper;
    }

    private List<String> assertCorrectNumArgs(List<String> args) {
        if (args.size() != 2) {
            log.err().println(strings.getf("General.invalidArgNum", COMMAND_NAME));
            return null;
        }
        return args;
    }

    private List<String> assertPropertyExists(List<String> args) {
        propertyName = args.get(0);
        value = args.get(1);
        if (!settingsHelper.settingExists(propertyName)) {
            log.err().println(strings.getf("SetProperty.notRecognised", propertyName));
            log.err().println();
            log.err().println(getUsageText());
            return null;
        }
        return args;
    }

    private List<String> setProperty(List<String> args) {
        try {
            Settings.Name name = Arrays.stream(Settings.Name.values())
                    .filter(n -> n.toString().equals(propertyName))
                    .findFirst()
                    .get();
            settings.get().setValue(name, value);
            log.err().println(strings.getf("SetProperty.successful", propertyName, value));
        } catch (TransformerException | CorruptSettingsException | InvalidSettingNameException e) {
            log.err().println(e.getMessage());
        }
        return args;
    }

    @Override
    public void execute(List<String> args) {
        executeBehaviourChain(behaviourChain, args);
    }

    @Override
    public String getUsageText() {
        return strings.getf("SetProperty.usage", COMMAND_NAME, settingsHelper.getSettingDescriptions());
    }


    @Override
    public String getDescriptionText() {
        return strings.get("SetProperty.description");
    }

    @Override
    protected List<String> getCommandAliases() {
        return Arrays.asList(COMMAND_NAME);
    }
}
