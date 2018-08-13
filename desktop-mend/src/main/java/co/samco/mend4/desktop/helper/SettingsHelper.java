package co.samco.mend4.desktop.helper;

import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.core.Settings;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.exception.SettingRequiredException;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SettingsHelper {
    private static final String SETTING_DESCRIPTION_PREFIX = "Settings.descriptions";

    private final I18N strings;
    private final Settings settings;

    @Inject
    public SettingsHelper(I18N strings, Settings settings) {
        this.strings = strings;
        this.settings = settings;
    }

    public void assertRequiredSettingsExist(Settings.Name[] required, String commandName) throws IOException,
            SettingRequiredException {
        for (Settings.Name n : required) {
            if (!settings.valueSet(n)) {
                throw new SettingRequiredException(strings.getf("General.dirRequired", n.toString(), commandName));
            }
        }
    }

    public boolean settingExists(String name) {
        return Arrays.stream(Settings.Name.values())
                .map(Settings.Name::toString)
                .anyMatch(n -> n.equals(name));
    }

    public String getSettingDescriptions() {
        return Arrays.stream(Settings.Name.values())
                .map(this::getFormattedSettingDescription)
                .collect(Collectors.joining(strings.getNewLine()));
    }

    public String getSettingValueWrapped(Settings.Name name) {
        try {
            if (settings.valueSet(name)) {
                return settings.getValue(name);
            } else return strings.get("StatePrinter.notFound");
        } catch (IOException | CorruptSettingsException e) {
            return strings.get("StatePrinter.Error");
        }
    }

    private String getFormattedSettingDescription(Settings.Name name) {
        return "\t" + name + "\t\t" + strings.get(SETTING_DESCRIPTION_PREFIX + name.toString());
    }

}
