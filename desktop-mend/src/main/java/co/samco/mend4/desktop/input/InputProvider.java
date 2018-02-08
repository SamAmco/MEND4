package co.samco.mend4.desktop.input;

public interface InputProvider {
    void clear();
    void addListener(InputListener l);
    void close();
}
