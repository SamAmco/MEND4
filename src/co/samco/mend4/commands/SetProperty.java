package co.samco.mend4.commands;

import java.util.ArrayList;

import javax.xml.transform.TransformerException;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.Settings.CorruptSettingsException;
import co.samco.mend4.core.Settings.InvalidSettingNameException;
import co.samco.mend4.core.Settings.UnInitializedSettingsException;

public class SetProperty extends Command
{
	@Override
	public void execute(ArrayList<String> args) 
	{
		if (printHelp(args))
			return;
		
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
			catch (TransformerException | CorruptSettingsException | InvalidSettingNameException | UnInitializedSettingsException e)
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
		return "Set the value of a property in your settings file.";	
	}
}
