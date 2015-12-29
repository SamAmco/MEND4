package co.samco.commands;

import java.io.IOException;
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import co.samco.mend.Command;
import co.samco.mend.Config;
import co.samco.mend.Settings;
import co.samco.mend.Settings.CorruptSettingsException;
import co.samco.mend.Settings.InvalidSettingNameException;

public class GetProperty extends Command
{

	@Override
	public void execute(ArrayList<String> args) 
	{
		if (args.size() < 1 || args.size() > 1)
		{
			System.err.println("Wrong number of arguments.");
			printUsage();
			return;
		}
		
		try 
		{
			//Do something like this:
			String value = null;
			for (int i = 0; i < Config.SETTINGS_NAMES_MAP.size(); i++)
			{
				if (Config.SETTINGS_NAMES_MAP.get(i).equals(args.get(0)))
				{
					value = Settings.instance().getValue(Config.Settings.values()[i]);
					break;
				}
			}
			
			if (value == null)
				System.err.println("Value not found.");
			else System.out.println(value);
		} 
		catch (CorruptSettingsException | ParserConfigurationException 
				| SAXException | IOException | InvalidSettingNameException e) 
		{
			System.err.println(e.getMessage());
		}
	}

	@Override
	public void printUsage() 
	{
		System.err.print("Usage: "); 
		System.err.println("mend get [property]");
	}

	@Override
	public void printDescription() 
	{
		System.out.println("Get the value of a property in your settings file.");
	}

}
