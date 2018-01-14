package co.samco.mend4droid.mend4;

import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.Settings;

/**
 * Created by sam on 18/06/17.
 */

public class AndroidSettings extends Settings
{
    public static final String ANDROID_VERSION = "0.2";
    public AndroidSettings(String dir) throws ParserConfigurationException, IOException, SAXException
    {
        super(new File(dir, Config.SETTINGS_FILE));
    }

    public String getPlatformDependentHeader()
    {
        return "DROID"+ANDROID_VERSION;
    }
}
