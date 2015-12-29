package co.samco.mend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import co.samco.commands.Decrypt;
import co.samco.commands.Encrypt;
import co.samco.commands.GetProperty;
import co.samco.commands.Lock;
import co.samco.commands.SetProperty;
import co.samco.commands.SetupMend;
import co.samco.commands.Unlock;

public class Main 
{
	private static Map<String, Class<?>> commands = new HashMap<String, Class<?>>();
	static
	{
		commands.put("unlock", Unlock.class);
		commands.put("setup", SetupMend.class);
		commands.put("lock", Lock.class);
		commands.put("set", SetProperty.class);
		commands.put("enc", Encrypt.class);
		commands.put("get", GetProperty.class);
		commands.put("dec", Decrypt.class);
	}
	
	public static void main(String[] args)
	{
		try
		{
			if (args.length < 1)
			{
				System.err.println("mend requires at least one argument. ");
				System.err.println();
				printUsage();
				return;
			}
			
			Iterator<Entry<String, Class<?>>> it = commands.entrySet().iterator();
			boolean found = false;
			while (it.hasNext())
			{
				Entry<String, Class<?>> entry = it.next();
				if (entry.getKey().equals(args[0]))
				{
					found = true;
					ArrayList<String> arguments = new ArrayList<String>();
					
					for (int i = 1; i < args.length; i++)
					{
						arguments.add(args[i]);
					}
					
					Command c = ((Command)entry.getValue().newInstance());
					c.execute(arguments);
				}
			}
			
			if (!found)
			{
				System.err.println("Command not found.");
				System.err.println();
				printUsage();
			}
		}
		catch (InstantiationException | IllegalAccessException e) 
		{
			System.err.println(e.getMessage());
		}
	}
	
	private static void printUsage() 
			throws InstantiationException, IllegalAccessException
	{
		System.err.println("Usage: mend <command> [<args>]");
		System.err.println();
		System.err.println("Commands:");
		Iterator<Entry<String, Class<?>>> it = commands.entrySet().iterator();
		while (it.hasNext())
		{
			Entry<String, Class<?>> entry = it.next();
			System.err.print("\t" + entry.getKey() + "\t");
			((Command)entry.getValue().newInstance()).printDescription();
		}
	}
}

/*
 * To get a list of enabled crypto algorithms:
 * for (Provider p : Security.getProviders())
		{
			for (Service s : p.getServices())
			{
				System.out.println(s.getAlgorithm());
			}
		}
 */








