package co.samco.mend4.commands;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;

import co.samco.mend4.desktop.Config;
import co.samco.mend4.desktop.Settings;

public class Lock extends Command
{
	@Override
	public void execute(ArrayList<String> args) 
	{
		if (printHelp(args))
			return;
		
		File privateKeyFile = new File(Config.CONFIG_PATH + Config.PRIVATE_KEY_FILE_DEC);
		if (!privateKeyFile.exists())
		{
			System.err.println("MEND did not appear to be unlocked.");
		}
		
		try
		{
			String shredCommand = Settings.instance().getValue(Config.Settings.SHREDCOMMAND);
			if (shredCommand == null)
			{
				System.err.println("You need to set the " + Config.SETTINGS_NAMES_MAP.get(Config.Settings.SHREDCOMMAND.ordinal())
					+ " property in your settings before you can shred files.");
				return;
			}
			
			tryShredFile(Config.PRIVATE_KEY_FILE_DEC, shredCommand);
			tryShredFile(Config.PUBLIC_KEY_FILE, shredCommand);
			
			if (!privateKeyFile.exists())
				System.out.println("MEND Locked.");
			else System.out.println("Locking may have failed, your private key file still exists.");
		}
		catch(Exception e)
		{
			System.err.println(e.getMessage());
		}
	}
	
	private void tryShredFile(String name, String shredCommand) throws Exception
	{
		//If there is already a file existent, just shred it and unlock again.
		File file = new File(Config.CONFIG_PATH + name);
		
		String[] shredCommandArgs = generateShredCommandArgs(Config.CONFIG_PATH + name, shredCommand);
		Process tr = Runtime.getRuntime().exec(shredCommandArgs);
		BufferedReader rd = new BufferedReader(new InputStreamReader(tr.getInputStream()));
		String s = rd.readLine();
		while (s != null)
		{
			System.out.println(s);
			s = rd.readLine();
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
