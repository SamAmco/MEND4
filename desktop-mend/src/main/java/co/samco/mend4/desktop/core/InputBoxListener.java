package co.samco.mend4.desktop.core;

public interface InputBoxListener {
    public void OnEnter(char[] text);

    public void OnShiftEnter(char[] text);

    public void OnCtrlEnter(char[] text);

    public void OnEscape();
}
