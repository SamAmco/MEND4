package co.samco.mend4.desktop.commands;

import java.util.*;
import java.util.function.Function;

import javax.inject.Inject;
import javax.xml.transform.TransformerException;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.impl.SettingsImpl.CorruptSettingsException;
import co.samco.mend4.core.impl.SettingsImpl.InvalidSettingNameException;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import dagger.Lazy;

public class SetProperty extends Command {
    public static final String COMMAND_NAME = "set";

    private final PrintStreamProvider log;
    private final I18N strings;
    private final Lazy<Settings> settings;

    private String propertyName;
    private String value;

    private final List<Function<List<String>, List<String>>> behaviourChain = Arrays.asList(
            a -> assertCorrectNumArgs(a),
            a -> assertPropertyExists(a),
            a -> setProperty(a)
    );

    @Inject
    public SetProperty(PrintStreamProvider log, I18N strings, Lazy<Settings> settings) {
        this.log = log;
        this.strings = strings;
        this.settings = settings;
    }

    private List<String> assertCorrectNumArgs(List<String> args) {
        if (args.size() != 2) {
            log.err().println(strings.getf("General.invalidArgNum", COMMAND_NAME));
            log.err().println();
            log.err().println(getUsageText());
            return null;
        }
        return args;
    }

    private List<String> assertPropertyExists(List<String> args) {
        propertyName = args.get(0);
        value = args.get(1);
        if (!Config.SETTINGS_NAMES_MAP.values().contains(propertyName)) {
            log.err().println(strings.getf("SetProperty.notRecognised", propertyName));
            log.err().println();
            log.err().println(getUsageText());
            return null;
        }
        return args;
    }

    private List<String> setProperty(List<String> args) {
        try {
            Integer propertyIndex = Config.SETTINGS_NAMES_MAP.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(propertyName))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .get();
            settings.get().setValue(Config.Settings.values()[propertyIndex], value);
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
        StringBuilder sb = new StringBuilder();
        sb.append(strings.getf("SetProperty.usage", COMMAND_NAME));
        sb.append(strings.getNewLine());
        sb.append(strings.getNewLine());
        sb.append(strings.get("SetProperty.recognisedProperties"));

        for (int i = 0; i < Config.Settings.values().length; i++) {
            sb.append(strings.getNewLine());
            sb.append("\t");
            sb.append(Config.SETTINGS_NAMES_MAP.get(i));
            sb.append("\t\t");
            sb.append(Config.SETTINGS_DESCRIPTIONS_MAP.get(i));
        }
        return sb.toString();
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
