package co.samco.mend4.core;

public class CorruptSettingsException extends Exception {
    public CorruptSettingsException(String message, String setting) {
        super(String.format(message, setting));
    }
}
