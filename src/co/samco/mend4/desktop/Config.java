package co.samco.mend4.desktop;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import co.samco.mend4.desktop.Settings.CorruptSettingsException;
import co.samco.mend4.desktop.Settings.InvalidSettingNameException;

public class Config 
{
	public static final String VERSION_NUMBER = "4.0.5";
	public static final String CONFIG_PATH = System.getProperty("user.home") + "/.MEND4/";
	public static final String SETTINGS_FILE = "Settings.xml";
	public static final String PRIVATE_KEY_FILE_DEC = "prKey";
	public static final String PUBLIC_KEY_FILE = "pubKey";
	public static final String PASSCHECK_TEXT = "How much wood could a wood chuck chuck if a wood chuck could chuck wood?";
	public static final byte[] PASSCHECK_SALT = new byte[] {
		    (byte)0xd7, (byte)0x73, (byte)0x31, (byte)0x8a,
		    (byte)0x2e, (byte)0xc8, (byte)0xef, (byte)0x99
		};
	
	public static final IvParameterSpec STANDARD_IV;
	
	static
	{
		byte[] iv = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
		STANDARD_IV = new IvParameterSpec(iv);
	}
	
	public static enum Settings
	{
		PUBLICKEY,
		PRIVATEKEY,
		CURRENTLOG,
		LOGDIR,
		ENCDIR,
		DECDIR,
		PASSCHECK,
		RSAKEYSIZE,
		AESKEYSIZE,
		PREFERREDRSA,
		PREFERREDAES,
		SHREDCOMMAND
	}
	
	public static final Map<Integer, String> SETTINGS_NAMES_MAP;
	public static final Map<Integer, String> SETTINGS_DESCRIPTIONS_MAP;
	static 
	{
        Map<Integer, String> m = new HashMap<Integer, String>();
        int i = 0;
        m.put(i++, "publickey");
        m.put(i++, "privatekey");
        m.put(i++, "currentlog");
        m.put(i++, "logdir");
        m.put(i++, "encdir");
        m.put(i++, "decdir");
        m.put(i++, "passcheck");
        m.put(i++, "rsakeysize");
        m.put(i++, "aeskeysize");
        m.put(i++, "preferredrsa");
        m.put(i++, "preferredaes");
        m.put(i++, "shredcommand");
        
        SETTINGS_NAMES_MAP = Collections.unmodifiableMap(m);
    }
	static
	{
		Map<Integer, String> m = new HashMap<Integer, String>();
        int i = 0;
        m.put(i++, "The RSA key used to encrypt encoded in url safe base64.");
        m.put(i++, "The RSA key used to decrypt encoded in url safe base64, and encrypted with AES using the MD5 hash of your password.");
        m.put(i++, "The log file currently being used.");
        m.put(i++, "The directory where mend expects all your log files to exist by default.");
        m.put(i++, "The directory where mend will output encrypted files.");
        m.put(i++, "The directory where mend will temporarily store decrypted files.");
        m.put(i++, "A piece of encrypted data used to check the validity of your password");
        m.put(i++, "The preferred key size to use for RSA. You probably won't want to touch this.");
        m.put(i++, "The preferred key size to use for AES. You probably won't want to touch this.");
        m.put(i++, "The preferred transform for RSA ciphers. You probably won't want to touch this. The full list of available transforms is specific to your jvm.");
        m.put(i++, "The preferred transform for AES ciphers. You probably won't want to touch this. The full list of available transforms is specific to your jvm.");
        m.put(i++, "The command that will be run to shred a file, where <filename> is the file to be shredded.");
        
        SETTINGS_DESCRIPTIONS_MAP = Collections.unmodifiableMap(m);
	}
	
	public static int AES_KEY_GEN_ITERATIONS = 65536;
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
			String storedRsaLimitStr = co.samco.mend4.desktop.Settings.instance().getValue(Settings.RSAKEYSIZE);
			if (storedRsaLimitStr != null)
				RSA_KEY_SIZE = Integer.parseInt(storedRsaLimitStr);		
			//otherwise use the max recommended available
			else
				RSA_KEY_SIZE = Cipher.getMaxAllowedKeyLength("RSA") < 4096 ? 2048 : 4096;
			
			
			//Set the aes key size
			//If the user has a set preference for the size then use that
			String storedAesLimitStr = co.samco.mend4.desktop.Settings.instance().getValue(Settings.AESKEYSIZE);
			if (storedRsaLimitStr != null)
				AES_KEY_SIZE = Integer.parseInt(storedAesLimitStr);		
			//otherwise use the max recommended available
			else
				AES_KEY_SIZE = Cipher.getMaxAllowedKeyLength("AES") < 256 ? 128 : 256;
			
			
			//Set the rsa algorithm
			//If the user has a set preference for the algo then use that
			String storedRsaAlgStr = co.samco.mend4.desktop.Settings.instance().getValue(Settings.PREFERREDRSA);
			if (storedRsaLimitStr != null)
				PREFERRED_RSA_ALG = storedRsaAlgStr;
			//otherwise use the max recommended available
			else
				PREFERRED_RSA_ALG = "RSA/ECB/PKCS1Padding";
			
			
			//Set the aes algorithm
			//If the user has a set preference for the algo then use that
			String storedAesAlgStr = co.samco.mend4.desktop.Settings.instance().getValue(Settings.PREFERREDAES);
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
