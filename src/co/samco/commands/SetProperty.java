package co.samco.commands;

import java.io.IOException;
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.xml.sax.SAXException;

import co.samco.mend.Command;
import co.samco.mend.Settings;
import co.samco.mend.Settings.CorruptSettingsException;

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
		
		if (true || propertyName.equals("logDir") || propertyName.equals("encDir") 
				|| propertyName.equals("decDir"))
		{
			try
			{
				Settings.instance().setValue(propertyName, value);
				System.out.println("Successfully set " + propertyName + " to " + value);
			}
			catch (ParserConfigurationException | SAXException | IOException | TransformerException | CorruptSettingsException e)
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
		System.err.println("\tlogDir\tThe directory where MEND will store and search for log files.");
		System.err.println("\tencDir\tThe directory where MEND will store and search for encrypted files. (Not including logs)");
		System.err.println("\tdecDir\tThe directory where MEND will store and search for decrypted files.");
	}


	@Override
	public void printDescription()
	{
		System.err.println("Configure the variables mend uses.");	
	}
}
