package co.samco.mend4.desktop.exception;

public class CommandFailedException extends Exception {
    public synchronized Throwable fillInStackTrace()  { return this; }
}
