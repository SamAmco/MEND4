package co.samco.commands;

import java.io.IOException;
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.xml.sax.SAXException;

import co.samco.mend.Command;
import co.samco.mend.Config;
import co.samco.mend.Settings;
import co.samco.mend.Settings.CorruptSettingsException;
import co.samco.mend.Settings.InvalidSettingNameException;

public class SetProperty extends Command
{
	@Override
	public void execute(ArrayList<String> args) 
	{
		if (args.size() != 2)
		{
			System.err.println("Wrong number of arguments:");
			System.err.println();
			printUsage();
			return;
		}
		
		String propertyName = args.get(0);
		String value = args.get(1);
		
		if (Config.SETTING_NAMES_MAP.values().contains(propertyName))
		{
			try
			{
				for (int i = 0; i < Config.SETTING_NAMES_MAP.size(); i++)
				{
					if (Config.SETTING_NAMES_MAP.get(i).equals(propertyName))
					{
						Settings.instance().setValue(Config.Settings.values()[i], value);
						System.out.println("Successfully set " + propertyName + " to " + value);
						break;
					}
				}
			}
			catch (ParserConfigurationException | SAXException | IOException 
					| TransformerException | CorruptSettingsException | InvalidSettingNameException e)
			{
				System.err.println(e.getMessage());
			}
		}
		else
		{
			System.err.println(propertyName + " is not a recognised property name.");
			System.err.println();
			printUsage();
		}
		
	}
	
	@Override
	public void printUsage() 
	{
		System.err.print("Usage: "); 
		System.err.println("mend set [property name] [value]");
		System.err.println();
		System.err.println("Recognized properties:");
		
		for (int i = 0; i < Config.Settings.values().length; i++)
		{
			System.err.print("\t");
			System.err.print(Config.SETTING_NAMES_MAP.get(i));
			System.err.print("\t\t");
			System.err.println(Config.SETTINGS_DESCRIPTIONS_MAP.get(i));
		}
	}


	@Override
	public void printDescription()
	{
		System.err.println("Configure the variables mend uses.");	
	}
}
