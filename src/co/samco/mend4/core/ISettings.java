package co.samco.mend4.core;

import javax.xml.transform.TransformerException;

import co.samco.mend4.core.Settings.CorruptSettingsException;
import co.samco.mend4.core.Settings.InvalidSettingNameException;

public interface ISettings 
{
	public void setValue(Config.Settings name, String value)throws TransformerException, CorruptSettingsException, InvalidSettingNameException;
	public String getValue(Config.Settings name) throws CorruptSettingsException, InvalidSettingNameException;

}
