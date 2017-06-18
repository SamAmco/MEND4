package co.samco.mend4.core;

public class Settings 
{
	private static ISettings instance;
	public static ISettings instance() throws UnInitializedSettingsException
	{
		if (instance == null)
			throw new UnInitializedSettingsException("Your Settings implementation has not been defined.");
		
		return instance;
	}
	
	public static void InitializeSettings(ISettings instance)
	{
		Settings.instance = instance;
	}
	
	public static class UnInitializedSettingsException extends Exception
	{
		private static final long serialVersionUID = -1209609585057442380L;

		public UnInitializedSettingsException(String message)
		{
			super(message);
		}
	}
	public static class CorruptSettingsException extends Exception
	{
		private static final long serialVersionUID = -7872915002684524393L;

		public CorruptSettingsException(String message)
		{
			super(message);
		}
	}
	public static class InvalidSettingNameException extends Exception
	{
		private static final long serialVersionUID = -396660409805269958L;

		public InvalidSettingNameException(String message)
		{
			super(message);
		}
	}
}
