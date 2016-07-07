package co.samco.commands;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import co.samco.mend.Command;
import co.samco.mend.Config;
import co.samco.mend.Settings;
import co.samco.mend.Settings.CorruptSettingsException;
import co.samco.mend.Settings.InvalidSettingNameException;

public class Lock extends Command
{
	@Override
	public void execute(ArrayList<String> args) 
	{
		if (printHelp(args))
			return;
		
		try
		{
			//If there is already a prKey.dex file existent, just shred it and unlock again.
			File privateKeyFile = new File(Config.CONFIG_PATH + "prKey.dec");
			if (!privateKeyFile.exists())
			{
				System.err.println("MEND did not appear to be unlocked.");
			}
			String shredCommand = Settings.instance().getValue(Config.Settings.SHREDCOMMAND);
			if (shredCommand == null)
			{
				System.err.println("You need to set the " + Config.SETTINGS_NAMES_MAP.get(Config.Settings.SHREDCOMMAND.ordinal())
					+ " property in your settings before you can shred files.");
				return;
			}
			String[] shredCommandArgs = generateShredCommandArgs(Config.CONFIG_PATH + "prKey.dec", shredCommand);
			Process tr = Runtime.getRuntime().exec(shredCommandArgs);
			BufferedReader rd = new BufferedReader(new InputStreamReader(tr.getInputStream()));
			String s = rd.readLine();
			while (s != null)
			{
				System.out.println(s);
				s = rd.readLine();
			}
			if (!privateKeyFile.exists())
				System.out.println("MEND Locked.");
		}
		catch(IOException | CorruptSettingsException | InvalidSettingNameException 
				| ParserConfigurationException | SAXException e)
		{
			System.err.println(e.getMessage());
		}
	}

	@Override
	public String getUsageText() 
	{
		return "Usage:\tmend lock";
	}

	@Override
	public String getDescriptionText() 
	{
		return "Shreds the decrypted private key. Requires shred to be installed.";	
	}
}
