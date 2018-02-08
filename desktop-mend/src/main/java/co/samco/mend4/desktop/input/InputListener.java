package co.samco.mend4.desktop.input;

public interface InputListener {
    void onLogAndClose(char[] text);

    void onLogAndClear(char[] text);
}
