package co.samco.mend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import co.samco.commands.Command;
import co.samco.commands.SetupMend;
import co.samco.commands.Unlock;

public class Main 
{
	private static Map<String, Class<?>> commands = new HashMap<String, Class<?>>();
	static
	{
		commands.put("unlock", Unlock.class);
		commands.put("setup", SetupMend.class);
	}
	
	public static void main(String[] args)
	{		
		//Map<String, String> env = System.getenv();
		
		Iterator<Entry<String, Class<?>>> it = commands.entrySet().iterator();
		while (it.hasNext())
		{
			Entry<String, Class<?>> entry = it.next();
			if (entry.getKey().equals(args[0]))
			{
				try 
				{
					ArrayList<String> arguments = new ArrayList<String>();
					
					for (int i = 1; i < args.length; i++)
					{
						arguments.add(args[i]);
					}
					
					Command c = ((Command)entry.getValue().newInstance());
					c.execute(arguments);
				}
				catch (InstantiationException | IllegalAccessException e) 
				{
					e.printStackTrace();
				}
			}
		}
	}

}
