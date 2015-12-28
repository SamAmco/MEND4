package co.samco.commands;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
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

import org.apache.commons.codec.binary.Base64;
import org.xml.sax.SAXException;

import co.samco.mend.Command;
import co.samco.mend.Config;
import co.samco.mend.InputBox;
import co.samco.mend.InputBoxListener;
import co.samco.mend.Settings;
import co.samco.mend.Settings.CorruptSettingsException;

public class Unlock extends Command implements InputBoxListener
{
	InputBox inputBox;
	@Override
	public void execute(ArrayList<String> args) 
	{
		inputBox = new InputBox(false, true, 200, 25);
		inputBox.addListener(this);
		inputBox.setVisible(true);
	}
	
	@Override
	public void printUsage() 
	{
		System.out.print("Usage: "); 
		System.out.println("mend unlock");
	}

	@Override
	public void OnEnter(char[] password) 
	{
		inputBox.close();
		try 
		{
			//If there is already a prKey.dec file existent, just shred it and unlock again.
			if (new File(Config.CONFIG_PATH + Config.PRIVATE_KEY_FILE_DEC).exists())
				new Lock().execute(new ArrayList<String>());
			
			//Check that the user has an encrypted private key.
			String hash = Settings.instance().getValue("passhash");
			if (hash == null)
			{
				System.err.println("Could not find the hash of your password (passhash) in your settings file.");
				return;
			}
			
			//Check that the users entered password hash matches the stored hash.
			byte[] bytesOfFileHash = Base64.decodeBase64(hash);

			MessageDigest md = MessageDigest.getInstance("MD5");
			String passwordString = new String(password);
			byte[] inputDigest = md.digest(passwordString.getBytes());
			
			if (!Arrays.equals(bytesOfFileHash, inputDigest))
			{
				System.err.println("Unlock Failed: Incorrect password.");
				return;
			}
			
			//Decrypt the private key with the password.
			MessageDigest sha = MessageDigest.getInstance("SHA-1");
	        byte[] key = sha.digest(passwordString.getBytes());
	        
	        SecretKeySpec secretKeySpec = new SecretKeySpec(Arrays.copyOf(key, 16), "AES");
	        Cipher cipher = Cipher.getInstance("AES");
	        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
	        
	        byte[] encryptedPrivateKey = Base64.decodeBase64(Settings.instance().getValue("privatekey"));
	        byte[] decryptedPrivateKey = cipher.doFinal(encryptedPrivateKey);
	        
	        //Write the decrypted private key to a file
			File privateKeyFile = new File(Config.CONFIG_PATH + Config.PRIVATE_KEY_FILE_DEC);
	        FileOutputStream fos = new FileOutputStream(privateKeyFile);
	        fos.write(decryptedPrivateKey);
	        fos.flush();
	        fos.close();
			
			System.out.println("MEND Unlocked.");
		} 
		catch (ParserConfigurationException | InvalidKeyException | IllegalBlockSizeException 
				| BadPaddingException | CorruptSettingsException | SAXException 
				| IOException | NoSuchAlgorithmException | NoSuchPaddingException e) 
		{
			System.err.println(e.getMessage());
		} 
	}
	
	@Override
	public void printDescription() 
	{
		System.err.println("To decrypt the private key.");	
	}

	@Override
	public void OnShiftEnter(char[] text) {}

	@Override
	public void OnCtrlEnter(char[] text) {}

	@Override
	public void OnEscape() {}

}
