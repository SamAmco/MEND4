package co.samco.commands;

import java.util.ArrayList;

import co.samco.mend.Command;

public class Decrypt extends Command
{
	@Override
	public void execute(ArrayList<String> args) 
	{
		//TODO allow the decryption of files.
		
		if (args.size() < 1)
		{
			System.err.println("Please provide the file to decrypt.");
			System.err.println(getUsageText());
		}
	}

	@Override
	public String getUsageText() 
	{
		return "Usage:\tmend dec <log_file>";
	}

	@Override
	public String getDescriptionText() 
	{
		return "To decrypt an encrypted log or other mend encrypted file.";
	}
}
