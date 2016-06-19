package co.samco.mend;

import java.util.ArrayList;

public abstract class Command 
{
	public abstract void execute(ArrayList<String> args);
	
	protected boolean printHelp(ArrayList<String> args)
	{
		if (args.contains("-h"))
		{
			System.err.println(getUsageText());
			return true;
		}
		return false;
	}
	
	public abstract String getUsageText();
	
	public abstract String getDescriptionText();
}
