package co.samco.mend4.desktop.helper;

import co.samco.mend4.core.Settings;
import co.samco.mend4.core.impl.SettingsImpl;
import co.samco.mend4.desktop.core.I18N;
import dagger.Lazy;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.stream.Collectors;

public class SettingsHelper {
    private static final String SETTING_DESCRIPTION_PREFIX = "Settings.descriptions";

    private final I18N strings;
    private final Lazy<Settings> settings;

    @Inject
    public SettingsHelper(I18N strings, Lazy<Settings> settings) {
        this.strings = strings;
        this.settings = settings;
    }

    public boolean settingExists(String name) {
        for (Settings.Name n : Settings.Name.values()) {
            if (n.toString().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public String getSettingDescriptions() {
        return Arrays.stream(Settings.Name.values())
                .map(this::getFormattedSettingDescription)
                .collect(Collectors.joining(strings.getNewLine()));
    }

    public String getSettingValueWrapped(Settings.Name name) {
        String value;
        try {
            value = settings.get().getValue(name);
        }
        catch (SettingsImpl.CorruptSettingsException | SettingsImpl.InvalidSettingNameException e) {
            value = strings.get("StatePrinter.Error");
        }
        return value;
    }

    private String getFormattedSettingDescription(Settings.Name name) {
        return "\t" + name + "\t\t" + strings.get(SETTING_DESCRIPTION_PREFIX + name.toString());
    }

}
