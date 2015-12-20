package co.samco.mend;

public interface InputBoxListener 
{
	public void OnEnter(char[] text);
	
	public void OnShiftEnter(char[] text);
	
	public void OnCtrlEnter(char[] text);
}
