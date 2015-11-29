package co.samco.mend;

import java.util.ArrayList;

public abstract class Command 
{
	protected static final String CONFIG_PATH = System.getProperty("user.home") + "/.MEND4/";
	protected static final String SETTINGS_FILE = "Settings.xml";
	protected static final String PRIVATE_KEY_FILE_ENC = "prKey.enc";
	protected static final String PRIVATE_KEY_FILE_DEC = "prKey.dec";
	
	public abstract void execute(ArrayList<String> args);
	
	public abstract void printUsage();
	
	public abstract void printDescription();
}
