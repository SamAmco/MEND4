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
			getUsageText();
			return;
		}
		
		String propertyName = args.get(0);
		String value = args.get(1);
		
		if (Config.SETTINGS_NAMES_MAP.values().contains(propertyName))
		{
			try
			{
				for (int i = 0; i < Config.SETTINGS_NAMES_MAP.size(); i++)
				{
					if (Config.SETTINGS_NAMES_MAP.get(i).equals(propertyName))
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
			getUsageText();
		}
		
	}
	
	@Override
	public String getUsageText() 
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Usage:\tmend set <property name> <value>");
		sb.append("\n");
		sb.append("\nRecognized properties:");
		
		for (int i = 0; i < Config.Settings.values().length; i++)
		{
			sb.append("\n\t");
			sb.append(Config.SETTINGS_NAMES_MAP.get(i));
			sb.append("\t\t");
			sb.append(Config.SETTINGS_DESCRIPTIONS_MAP.get(i));
		}
		return sb.toString();
	}


	@Override
	public String getDescriptionText()
	{
		return "Configure the variables mend uses.";	
	}
}
