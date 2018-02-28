package co.samco.mend4.core.exception;

public class MalformedLogFileException extends Exception {
    private static final long serialVersionUID = 9219333934024822210L;

    public MalformedLogFileException() {
        super("Log file is malformed.");
    }
}
