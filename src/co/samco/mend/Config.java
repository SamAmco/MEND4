package co.samco.mend;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Config 
{
	public static final String CONFIG_PATH = "";//System.getProperty("user.home") + "/.MEND4/";
	public static final String SETTINGS_FILE = "Settings.xml";
	public static final String PRIVATE_KEY_FILE_DEC = "prKey.dec";
	
	public static enum Settings
	{
		PUBLICKEY,
		PRIVATEKEY,
		CURRENTLOG,
		LOGDIR,
		ENCDIR,
		DECDIR,
		PASSHASH
	}
	
	public static final Map<Integer, String> SETTING_NAMES_MAP;
	public static final Map<Integer, String> SETTINGS_DESCRIPTIONS_MAP;
	static 
	{
        Map<Integer, String> m = new HashMap<Integer, String>();
        m.put(0, "publickey");
        m.put(1, "privatekey");
        m.put(2, "currentlog");
        m.put(3, "logdir");
        m.put(4, "encdir");
        m.put(5, "decdir");
        m.put(6, "passhash");
        
        SETTING_NAMES_MAP = Collections.unmodifiableMap(m);
    }
	static
	{
		Map<Integer, String> m = new HashMap<Integer, String>();
        m.put(0, "The RSA key used to encrypt encoded in url safe base64.");
        m.put(1, "The RSA key used to decrypt encoded in url safe base64, and encrypted with AES using the MD5 hash of your password.");
        m.put(2, "The log file currently being used.");
        m.put(3, "The directory where mend expects all your log files to exist by default.");
        m.put(4, "The directory where mend will output encrypted files.");
        m.put(5, "The directory where mend will temporarily store decrypted files.");
        m.put(6, "A SHA hash of your password.");
        
        SETTINGS_DESCRIPTIONS_MAP = Collections.unmodifiableMap(m);
	}
	
	//TODO We need to have static final variables to describe the key sizes to use for various crypto algorithms (Namely RSA and AES)
	//These values need to first be set based on the settings file, then overriden by the jvm limits, then default to the most secure
	//reasonable values available p.s. please use 4096 rsa keys because the same key is intended to be kept for many years. 
	
}
