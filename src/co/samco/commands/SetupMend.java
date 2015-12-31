package co.samco.commands;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.xml.sax.SAXException;

import co.samco.mend.Command;
import co.samco.mend.Config;
import co.samco.mend.Settings;
import co.samco.mend.Settings.CorruptSettingsException;
import co.samco.mend.Settings.InvalidSettingNameException;

import org.apache.commons.codec.binary.Base64;

public class SetupMend extends Command
{

	@Override
	public void execute(ArrayList<String> args) 
	{
		if (args.size() != 1)
		{
			System.err.println("Please provide a password.");
			getUsageText();
			return;
		}
		
		String password = args.get(0);
		
		//TODO What if they're already set up? Maybe the user should be warned.
		//TODO its probably here that we'll want to warn the user if they don't have unlimited crypto policies installed
		try 
		{
			//Ensure the settings path exists
			System.out.println("Creating Directory: " + Config.CONFIG_PATH);
			new File(Config.CONFIG_PATH).mkdirs();
			
			//Generate an RSA key pair.
			KeyPairGenerator keyGen;
			keyGen = KeyPairGenerator.getInstance("RSA");
	        keyGen.initialize(Config.RSA_KEY_SIZE);
	        
	        KeyPair keyPair = keyGen.genKeyPair();
	        
			//Encrypt the private key with the password.
	        MessageDigest sha = MessageDigest.getInstance("SHA-1");
	        byte[] key = sha.digest(password.getBytes());
	        
	        SecretKeySpec secretKeySpec = new SecretKeySpec(Arrays.copyOf(key, 16), "AES");
	        Cipher cipher = Cipher.getInstance(Config.PREFERRED_AES_ALG);
	        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
	        
	        //Write the encrypted private key to settings
	        byte[] encryptedPrivateKey = cipher.doFinal(keyPair.getPrivate().getEncoded());
	        Settings.instance().setValue(Config.Settings.PRIVATEKEY, Base64.encodeBase64URLSafeString(encryptedPrivateKey));
	        
	        //Once we've used the aes algorithm once, we need to make sure the same algorithm will be selected in future.
	        //for simplicity i want to set all algorithms and key sizes in stone almost at this point
	        Settings.instance().setValue(Config.Settings.PREFERREDAES, Config.PREFERRED_AES_ALG);
	        Settings.instance().setValue(Config.Settings.PREFERREDRSA, Config.PREFERRED_RSA_ALG);
	        Settings.instance().setValue(Config.Settings.AESKEYSIZE, Integer.toString(Config.AES_KEY_SIZE));
	        Settings.instance().setValue(Config.Settings.RSAKEYSIZE, Integer.toString(Config.RSA_KEY_SIZE));

			//Add a passhash element to the options file containing the hash of the password.
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] passwordMD5 = md.digest(password.getBytes());
			String passwordMD5String = Base64.encodeBase64URLSafeString(passwordMD5);
			Settings.instance().setValue(Config.Settings.PASSHASH, passwordMD5String);
			
			//Add a publickey element to the settings file containing the public key.
			Settings.instance().setValue(Config.Settings.PUBLICKEY, Base64.encodeBase64URLSafeString(keyPair.getPublic().getEncoded()));
			
			System.out.println("MEND Successfully set up.");
	        
		} 
		catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException 
				| IllegalBlockSizeException | TransformerException | CorruptSettingsException 
				| InvalidSettingNameException | ParserConfigurationException 
				| SAXException | IOException | BadPaddingException 
				 e) 
		{
			System.err.println(e.getMessage());
		} 
	}
	
	@Override
	public String getUsageText()
	{
		return "Usage: " +  
			"\nmend setup <password>";
	}
	
	@Override
	public String getDescriptionText() 
	{
		return "Run this command first. It creates some basic config necessary.";	
	}
	
}
