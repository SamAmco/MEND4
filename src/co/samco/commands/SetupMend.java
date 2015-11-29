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
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import co.samco.mend.Command;

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
			System.out.println("Creating Directory: " + CONFIG_PATH);
			new File(CONFIG_PATH).mkdirs();
			
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
	        File privateKeyFile = new File(CONFIG_PATH + PRIVATE_KEY_FILE_ENC);
	        System.out.println("Creating File: " + privateKeyFile.getAbsolutePath());
	        FileOutputStream fos = new FileOutputStream(privateKeyFile);
	        fos.write(encryptedPrivateKey);
	        fos.flush();
	        fos.close();
	        
			//Generate a settings file.
	        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
	        
			Document doc = docBuilder.newDocument();
			Element rootElement = doc.createElement("settings");
			doc.appendChild(rootElement);
			
			
			//Add a passhash element to the options file containing the hash of the password.
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] passwordMD5 = md.digest(password.getBytes());
			String passwordMD5String = Base64.encodeBase64URLSafeString(passwordMD5);
			
			Element passHash = doc.createElement("passhash");
			passHash.setAttribute("value", passwordMD5String);
			rootElement.appendChild(passHash);
			
			
			//Add a publickey element to the options file containing the public key.
			Element publicKey = doc.createElement("publickey");
			publicKey.setAttribute("value", Base64.encodeBase64URLSafeString(keyPair.getPublic().getEncoded()));
			rootElement.appendChild(publicKey);
			
			
			//Write the settings file to disk.
			File settingsFile = new File(CONFIG_PATH + SETTINGS_FILE);
	        System.out.println("Creating File: " + settingsFile.getAbsolutePath());
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(doc);
			StreamResult result = new StreamResult(settingsFile);

			transformer.transform(source, result);
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
