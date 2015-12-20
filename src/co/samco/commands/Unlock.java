package co.samco.commands;

import java.io.File;
import java.io.FileInputStream;
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
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.w3c.dom.Node;

import co.samco.mend.Command;
import co.samco.mend.InputBox;
import co.samco.mend.InputBoxListener;

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
			if (new File(CONFIG_PATH + PRIVATE_KEY_FILE_DEC).exists())
				new Lock().execute(new ArrayList<String>());
			
			//Check that the user has an options file.
			File f = new File(CONFIG_PATH + SETTINGS_FILE);
			if (!f.exists())
			{
				System.err.println("Could not find " + SETTINGS_FILE);
				return;
			}

			//Check that the options file contains a password hash.
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			
			Document doc = dBuilder.parse(f);
			doc.getDocumentElement().normalize();

			//Check that the user has an encrypted private key.
			NodeList nList = doc.getElementsByTagName("passhash");
			
			if (nList.getLength() <= 0)
			{
				System.err.println("Could not find your passhash, please ensure your options file is configured correctly.");
				return;
			}
			if(nList.getLength() > 1)
			{
				System.err.println("Invalid Options file (contains multiple definitions for 'passhash').");
				return;
			}

			Node node = nList.item(0);
			if (node.getNodeType() != Node.ELEMENT_NODE)
			{
				System.err.println("Invalid Options file. passhash is not a valid element node.");
				return;
			}
			
			Element el = (Element)node;
			String hash = el.getAttribute("value");
			
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
	        
	        byte[] encryptedPrivateKey = IOUtils.toByteArray(new FileInputStream(new File(CONFIG_PATH + PRIVATE_KEY_FILE_ENC)));
	        byte[] decryptedPrivateKey = cipher.doFinal(encryptedPrivateKey);
	        
	        //Write the decrypted private key to a file
			File privateKeyFile = new File(CONFIG_PATH + PRIVATE_KEY_FILE_DEC);
	        FileOutputStream fos = new FileOutputStream(privateKeyFile);
	        fos.write(decryptedPrivateKey);
	        fos.flush();
	        fos.close();
			
			System.out.println("MEND Unlocked.");
		} 
		catch (ParserConfigurationException e) 
		{
			System.err.println(e.getMessage());
		} 
		catch (SAXException e) {
			System.err.println(e.getMessage());
		} 
		catch (IOException e) 
		{	
			System.err.println(e.getMessage());
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

}
