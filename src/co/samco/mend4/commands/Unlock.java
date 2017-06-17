package co.samco.mend4.commands;

import java.io.File;
import java.io.FileOutputStream;
import java.security.spec.KeySpec;
import java.util.ArrayList;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;

import co.samco.mend4.desktop.Config;
import co.samco.mend4.desktop.InputBox;
import co.samco.mend4.desktop.Settings;

public class Unlock extends Command
{
	InputBox inputBox;
	@Override
	public void execute(ArrayList<String> args) 
	{
		if (printHelp(args))
			return;
		
		char[] password = System.console().readPassword("Please enter your password: ");
		
		try 
		{
			String passCheck = Settings.instance().getValue(Config.Settings.PASSCHECK);
			byte[] compCheck = Base64.decodeBase64(passCheck);
			
			//generate an aes key from the password
			SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
			KeySpec spec = new PBEKeySpec(password, Config.PASSCHECK_SALT, Config.AES_KEY_GEN_ITERATIONS, Config.AES_KEY_SIZE);
			SecretKey tmp = factory.generateSecret(spec);
			SecretKey aesKey = new SecretKeySpec(tmp.getEncoded(), "AES");
			
			//use it to decrypt the text
			Cipher aesCipher = Cipher.getInstance(Config.PREFERRED_AES_ALG);
			aesCipher.init(Cipher.DECRYPT_MODE, aesKey, Config.STANDARD_IV);
			
			byte[] plainText = aesCipher.doFinal(compCheck);
			
			if (!Config.PASSCHECK_TEXT.equals(new String(plainText, "UTF-8")))
	        {
	        	System.err.println("Incorrect password");
	        	return;
	        }
			
			//If there is already a prKey file existent, just shred it and unlock again.
			if (new File(Config.CONFIG_PATH + Config.PRIVATE_KEY_FILE_DEC).exists() || new File(Config.CONFIG_PATH + Config.PUBLIC_KEY_FILE).exists())
				new Lock().execute(new ArrayList<String>());
			
			//Decrypt the private key with the password.        
			byte[] encryptedPrivateKey = Base64.decodeBase64(Settings.instance().getValue(Config.Settings.PRIVATEKEY));
	        byte[] decryptedPrivateKey = aesCipher.doFinal(encryptedPrivateKey);
	        byte[] publicKey = Base64.decodeBase64(Settings.instance().getValue(Config.Settings.PUBLICKEY));
	        
	        //Write the decrypted private key to a file
			File privateKeyFile = new File(Config.CONFIG_PATH + Config.PRIVATE_KEY_FILE_DEC);
			File publicKeyFile = new File(Config.CONFIG_PATH + Config.PUBLIC_KEY_FILE);
	        FileOutputStream prfos = null;
	        try
	        {
	        	prfos = new FileOutputStream(privateKeyFile);
	        	prfos.write(decryptedPrivateKey);
	        	
	        	FileOutputStream pubfos = null;
	        	try
	        	{
	        		pubfos = new FileOutputStream(publicKeyFile);
	        		pubfos.write(publicKey);
	        	}
	        	finally
	        	{
	        		if (pubfos != null)
	        		{
	        			pubfos.flush();
	        			pubfos.close();
	        		}
	        	}
	        }
	        finally
	        {
	        	if (prfos != null)
	        	{
			        prfos.flush();
			        prfos.close();
	        	}
	        }
			
			System.out.println("MEND Unlocked.");
		} 
		catch (Exception e) 
		{
			System.err.println(e.getMessage());
		} 
	}
	
	@Override
	public String getUsageText() 
	{
		return "Usage:\tmend unlock";
	}
	
	@Override
	public String getDescriptionText() 
	{
		return "To decrypt the private key.";	
	}

}
