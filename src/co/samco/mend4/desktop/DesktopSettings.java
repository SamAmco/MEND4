package co.samco.mend4.desktop;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.Settings;

/**
 * A singleton for accessing the settings file
 * @author sam
 *
 */
public class DesktopSettings extends Settings
{
	public static final String DESKTOP_VERSION = "0.2.b";
	public DesktopSettings() throws ParserConfigurationException, SAXException, IOException
	{
		super(new File(Config.CONFIG_PATH + Config.SETTINGS_FILE));
	}

	public String getPlatformDependentHeader() 
	{
		return "DESKT"+DESKTOP_VERSION;
	}
}
























