package co.samco.commands;

import java.util.ArrayList;
import java.util.Scanner;

public class EncryptFromStdIn extends Encrypt
{
	@Override
	public void execute(ArrayList<String> args) 
	{
		if (printHelp(args))
			return;
		
		readOptions(args);
		
		Scanner scanner = new Scanner(System.in);
		StringBuilder sb = new StringBuilder();
		
		while (scanner.hasNextLine())
		{
			sb.append(scanner.nextLine());
			sb.append(System.getProperty("line.separator"));
		}
		scanner.close();
		
		encryptTextToLog(sb.toString().toCharArray(), dropHeader);
	}
	
	@Override
	public String getUsageText() 
	{
		return "Usage:\tmend enci";
	}

	@Override
	public String getDescriptionText() 
	{
		return "To encrypt text to your current log from stdin.";
	}
}
