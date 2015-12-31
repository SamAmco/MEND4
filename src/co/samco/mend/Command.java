package co.samco.mend;

import java.util.ArrayList;

public abstract class Command 
{
	public abstract void execute(ArrayList<String> args);
	
	public abstract String getUsageText();
	
	public abstract String getDescriptionText();
}
