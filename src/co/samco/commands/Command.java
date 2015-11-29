package co.samco.commands;

import java.util.ArrayList;

public abstract class Command 
{
	public abstract void execute(ArrayList<String> args);
	public void printUsage() 
	{
		System.err.println("Usage: "); 
	}
}
