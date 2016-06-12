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
		super.execute(args);
		if(!continueExecution)
			return;
		
		if (args.size() < 1 || args.size() > 1)
		{
			System.err.println("Wrong number of arguments.");
			getUsageText();
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
	public String getUsageText() 
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Usage:\tmend get <property>");
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
		return "Get the value of a property in your settings file.";
	}

}
