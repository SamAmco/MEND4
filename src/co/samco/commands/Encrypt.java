package co.samco.commands;

import java.util.ArrayList;

import co.samco.mend.Command;
import co.samco.mend.InputBox;
import co.samco.mend.InputBoxListener;

public class Encrypt extends Command implements InputBoxListener
{
	InputBox inputBox;

	@Override
	public void execute(ArrayList<String> args) 
	{
		//TODO allow file encryption
		
		if (args.contains("-d"))
		{
			int dPos = args.indexOf("-d");
			
			if (args.size() < dPos + 1)
			{
				System.err.println("You must provide the text you wish to encrypt after the -d option");
				return;
			}
			encryptTextToLog(args.get(args.indexOf("-d")+1).toCharArray());
			return;
		}
		
		inputBox = new InputBox(true, false, 800, 250);
		inputBox.addListener(this);
		inputBox.setVisible(true);
	}

	@Override
	public void printUsage() 
	{
		
	}

	@Override
	public void printDescription() 
	{
		
	}
	
	@Override
	public void OnEnter(char[] password) {}

	@Override
	public void OnShiftEnter(char[] text) 
	{
		inputBox.close();
	}

	@Override
	public void OnCtrlEnter(char[] text) 
	{
		inputBox.clear();
	}
	
	private void encryptTextToLog(char[] text)
	{
		
	}

	@Override
	public void OnEscape()
	{
		inputBox.close();
	}
	
}
