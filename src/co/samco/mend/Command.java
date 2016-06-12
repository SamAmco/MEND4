package co.samco.mend;

import java.util.ArrayList;

public abstract class Command 
{
	protected boolean continueExecution = true;
	public void execute(ArrayList<String> args)
	{
		if (args.contains("-h"))
		{
			System.err.println(getUsageText());
			continueExecution = false;
		}
	}
	
	public abstract String getUsageText();
	
	public abstract String getDescriptionText();
}
