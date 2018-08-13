package co.samco.mend4.desktop.core;

import java.util.Locale;
import java.util.ResourceBundle;

public class I18N {
    private final ResourceBundle strings;

    public I18N(String language, String country) {
        strings = ResourceBundle.getBundle("strings", new Locale(language, country));
    }

    public String getf(String name, Object... args) {
        return String.format(strings.getString(name), args);
    }

    public String get(String name) {
        return strings.getString(name);
    }

    public String getNewLine(int num) {
        return new String(new char[num]).replace("\0", getNewLine());
    }

    public String getNewLine() {
        return System.getProperty("line.separator");
    }
}
