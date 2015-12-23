package co.samco.commands;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;

import org.xml.sax.SAXException;

import co.samco.mend.Command;
import co.samco.mend.Config;
import co.samco.mend.Settings;
import co.samco.mend.Settings.CorruptSettingsException;

import org.apache.commons.codec.binary.Base64;

public class SetupMend extends Command
{

	@Override
	public void execute(ArrayList<String> args) 
	{
		if (args.size() != 1)
		{
			System.err.println("Please provide a password.");
			printUsage();
			return;
		}
		
		String password = args.get(0);
		
		//TODO What if they're already set up? Maybe the user should be warned.
		try 
		{
			//Ensure the settings path exists
			System.out.println("Creating Directory: " + Config.CONFIG_PATH);
			new File(Config.CONFIG_PATH).mkdirs();
			
			//Generate an RSA key pair.
			KeyPairGenerator keyGen;
			keyGen = KeyPairGenerator.getInstance("RSA");
	        keyGen.initialize(2048);
	        
	        KeyPair keyPair = keyGen.genKeyPair();
	        
	        
			//Encrypt the private key with the password.
	        MessageDigest sha = MessageDigest.getInstance("SHA-1");
	        byte[] key = sha.digest(password.getBytes());
	        
	        SecretKeySpec secretKeySpec = new SecretKeySpec(Arrays.copyOf(key, 16), "AES");
	        Cipher cipher = Cipher.getInstance("AES");
	        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
	        
	        byte[] encryptedPrivateKey = cipher.doFinal(keyPair.getPrivate().getEncoded());
	        
	        
	        //Write the encrypted private key to a file
	        File privateKeyFile = new File(Config.CONFIG_PATH + Config.PRIVATE_KEY_FILE_ENC);
	        System.out.println("Creating File: " + privateKeyFile.getAbsolutePath());
	        FileOutputStream fos = new FileOutputStream(privateKeyFile);
	        fos.write(encryptedPrivateKey);
	        fos.flush();
	        fos.close();
	        
			//Add a passhash element to the options file containing the hash of the password.
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] passwordMD5 = md.digest(password.getBytes());
			String passwordMD5String = Base64.encodeBase64URLSafeString(passwordMD5);
			Settings.instance().setValue("passhash", passwordMD5String);
			
			//Add a publickey element to the settings file containing the public key.
			Settings.instance().setValue("publickey", Base64.encodeBase64URLSafeString(keyPair.getPublic().getEncoded()));
			
			System.out.println("MEND Successfully set up.");
	        
		} 
		catch (NoSuchAlgorithmException e) 
		{
			System.err.println(e.getMessage());
		} 
		catch (NoSuchPaddingException e) 
		{
			System.err.println(e.getMessage());
		} 
		catch (InvalidKeyException e) 
		{
			System.err.println(e.getMessage());
		} 
		catch (IllegalBlockSizeException e) 
		{
			System.err.println(e.getMessage());
		} 
		catch (BadPaddingException e) 
		{
			System.err.println(e.getMessage());
		}
		catch (FileNotFoundException e) 
		{
			System.err.println(e.getMessage());
		}
		catch (IOException e) 
		{
			System.err.println(e.getMessage());
		} 
		catch (ParserConfigurationException e) 
		{
			System.err.println(e.getMessage());
		} 
		catch (TransformerConfigurationException e) 
		{
			System.err.println(e.getMessage());
		} 
		catch (TransformerException e) 
		{
			System.err.println(e.getMessage());
		} 
		catch (SAXException e) 
		{
			System.err.println(e.getMessage());
		} 
		catch (CorruptSettingsException e) 
		{
			System.err.println(e.getMessage());
		}
		
	}
	
	@Override
	public void printUsage()
	{
		System.err.print("Usage: "); 
		System.err.println("mend setup [password]");
	}
	
	@Override
	public void printDescription() 
	{
		System.err.println("Run this command first. It creates some basic config necessary.");	
	}
	
}
