package co.samco.commands;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import co.samco.mend.Command;
import co.samco.mend.Config;

public class Lock extends Command
{
	@Override
	public void execute(ArrayList<String> args) 
	{
		try
		{
			//If there is already a prKey.dex file existent, just shred it and unlock again.
			File privateKeyFile = new File(Config.CONFIG_PATH + "prKey.dec");
			if (!privateKeyFile.exists())
			{
				System.err.println("MEND did not appear to be unlocked.");
			}
			System.out.println("Shredding prKey.dec");
			System.out.println();
			//TODO this probably needs re-thinking, because it's not cross platform or even reliably installed..
			//I would consider maybe just exposing the command to the user and default to shred. I don't fancy writing my own.
			Process tr = Runtime.getRuntime().exec(new String[]{"shred", "-u", Config.CONFIG_PATH + "prKey.dec"});
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
		catch(IOException e)
		{
			System.err.println(e.getMessage());
		}
	}

	@Override
	public String getUsageText() 
	{
		return "Usage: " +  
			"\nmend lock";
	}

	@Override
	public String getDescriptionText() 
	{
		return "Shreds the decrypted private key. Requires shred to be installed.";	
	}
}
