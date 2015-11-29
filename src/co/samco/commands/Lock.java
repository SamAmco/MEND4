package co.samco.commands;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import co.samco.mend.Command;

public class Lock extends Command
{
	@Override
	public void execute(ArrayList<String> args) 
	{
		try
		{
			//If there is already a prKey.dex file existent, just shred it and unlock again.
			File privateKeyFile = new File(CONFIG_PATH + "prKey.dec");
			if (!privateKeyFile.exists())
			{
				System.err.println("MEND did not appear to be unlocked.");
			}
			System.out.println("Shredding prKey.dec");
			System.out.println();
			Process tr = Runtime.getRuntime().exec(new String[]{"shred", "-u", CONFIG_PATH + "prKey.dec"});
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
	public void printUsage() 
	{
		System.out.print("Usage: "); 
		System.out.println("mend lock");
	}

	@Override
	public void printDescription() 
	{
		System.err.println("Shreds the decrypted private key. Requires shred to be installed.");	
	}
}
