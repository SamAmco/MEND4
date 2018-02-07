package co.samco.mend4.desktop.core;

import javax.inject.Inject;
import java.util.Locale;
import java.util.ResourceBundle;

public class I18N {
    private final ResourceBundle strings;

    @Inject
    public I18N(String language, String country) {
        strings = ResourceBundle.getBundle("strings", new Locale(language, country));
    }

    public String getf(String name, Object... args) {
        return String.format(strings.getString(name), args);
    }

    public String get(String name) {
        return strings.getString(name);
    }
}
