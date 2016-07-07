package co.samco.commands;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import co.samco.mend.Command;
import co.samco.mend.Config;
import co.samco.mend.Settings;
import co.samco.mend.Settings.CorruptSettingsException;
import co.samco.mend.Settings.InvalidSettingNameException;

public class Clean extends Command
{

	@Override
	public void execute(ArrayList<String> args) 
	{
		if (printHelp(args))
			return;
		
		
		try 
		{
			File dir = new File(Settings.instance().getValue(Config.Settings.DECDIR));
			File[] directoryListing = dir.listFiles();
			if (directoryListing == null)
			{
				System.err.println("Could not find the directory: " + dir.getAbsolutePath());
				return;
			}
			for (File child : directoryListing) 
			{
				System.err.println("Cleaning: " + child.getAbsolutePath());
				String shredCommand = Settings.instance().getValue(Config.Settings.SHREDCOMMAND);
				if (shredCommand == null)
				{
					System.err.println("You need to set the " + Config.SETTINGS_NAMES_MAP.get(Config.Settings.SHREDCOMMAND.ordinal())
						+ " property in your settings before you can shred files.");
					return;
				}
				String[] shredCommandArgs = generateShredCommandArgs(child.getAbsolutePath(), shredCommand);
				Process tr = Runtime.getRuntime().exec(shredCommandArgs);
				tr.waitFor();
			}
			System.err.println("Cleaning Complete");
		} 
		catch (CorruptSettingsException | InvalidSettingNameException | ParserConfigurationException
				| SAXException | IOException | InterruptedException e) 
		{
			System.err.println(e.getMessage());
		}
	}

	@Override
	public String getUsageText() 
	{
		return "Usage:\tmend clean";
	}

	@Override
	public String getDescriptionText() 
	{
		return "Runs the " + Config.SETTINGS_NAMES_MAP.get(Config.Settings.SHREDCOMMAND.ordinal()) + " on every file in your decrypt directory.";
	}
	

}
