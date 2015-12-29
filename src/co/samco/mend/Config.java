package co.samco.mend;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import co.samco.mend.Settings.CorruptSettingsException;
import co.samco.mend.Settings.InvalidSettingNameException;

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
		PASSHASH,
		RSAKEYSIZE,
		AESKEYSIZE,
		PREFERREDRSA,
		PREFERREDAES
	}
	
	public static final Map<Integer, String> SETTINGS_NAMES_MAP;
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
        m.put(7, "rsakeysize");
        m.put(8, "aeskeysize");
        m.put(9, "preferredrsa");
        m.put(10, "preferredaes");
        
        SETTINGS_NAMES_MAP = Collections.unmodifiableMap(m);
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
        m.put(7, "The preferred key size to use for RSA. You probably won't want to touch this.");
        m.put(8, "The preferred key size to use for AES. You probably won't want to touch this.");
        m.put(9, "The preferred transform for RSA ciphers. You probably won't want to touch this. The full list of available transforms is specific to your jvm.");
        m.put(10, "The preferred transform for AES ciphers. You probably won't want to touch this. The full list of available transforms is specific to your jvm.");
        
        SETTINGS_DESCRIPTIONS_MAP = Collections.unmodifiableMap(m);
	}
	
	public static String PREFERRED_RSA_ALG;
	public static String PREFERRED_AES_ALG;
	public static int RSA_KEY_SIZE;
	public static int AES_KEY_SIZE;
	static
	{
		try
		{			
			//Set the rsa key size
			//If the user has a set preference for the size then use that
			String storedRsaLimitStr = co.samco.mend.Settings.instance().getValue(Settings.RSAKEYSIZE);
			if (storedRsaLimitStr != null)
				RSA_KEY_SIZE = Integer.parseInt(storedRsaLimitStr);		
			//otherwise use the max recommended available
			else
				RSA_KEY_SIZE = Cipher.getMaxAllowedKeyLength("RSA") < 4096 ? 2048 : 4096;
			
			
			//Set the aes key size
			//If the user has a set preference for the size then use that
			String storedAesLimitStr = co.samco.mend.Settings.instance().getValue(Settings.AESKEYSIZE);
			if (storedRsaLimitStr != null)
				AES_KEY_SIZE = Integer.parseInt(storedAesLimitStr);		
			//otherwise use the max recommended available
			else
				AES_KEY_SIZE = Cipher.getMaxAllowedKeyLength("AES") < 256 ? 128 : 256;
			
			
			//Set the rsa algorithm
			//If the user has a set preference for the algo then use that
			String storedRsaAlgStr = co.samco.mend.Settings.instance().getValue(Settings.PREFERREDRSA);
			if (storedRsaLimitStr != null)
				PREFERRED_RSA_ALG = storedRsaAlgStr;
			//otherwise use the max recommended available
			else
				PREFERRED_RSA_ALG = "RSA/ECB/PKCS1Padding";
			
			
			//Set the aes algorithm
			//If the user has a set preference for the algo then use that
			String storedAesAlgStr = co.samco.mend.Settings.instance().getValue(Settings.PREFERREDAES);
			if (storedAesAlgStr != null)
				PREFERRED_AES_ALG = storedAesAlgStr;
			//otherwise use the max recommended available
			else
				PREFERRED_AES_ALG = "AES/CTR/NoPadding";
			
		}
		catch (NoSuchAlgorithmException | NumberFormatException | CorruptSettingsException 
				| InvalidSettingNameException | ParserConfigurationException | SAXException 
				| IOException e)
		{
			System.err.println(e.getMessage());
		}
	}
}
