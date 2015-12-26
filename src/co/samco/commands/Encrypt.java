package co.samco.commands;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.codec.binary.Base64;
import org.xml.sax.SAXException;

import co.samco.mend.Command;
import co.samco.mend.InputBox;
import co.samco.mend.InputBoxListener;
import co.samco.mend.Settings;
import co.samco.mend.Settings.CorruptSettingsException;

public class Encrypt extends Command implements InputBoxListener
{
	InputBox inputBox;

	@Override
	public void execute(ArrayList<String> args) 
	{
		//TODO allow file encryption
		
		if (args.contains("-d"))
		{
			int dPos = args.indexOf("-d");
			
			if (args.size() < dPos + 1)
			{
				System.err.println("You must provide the text you wish to encrypt after the -d option");
				return;
			}
			encryptTextToLog(args.get(args.indexOf("-d")+1).toCharArray());
			return;
		}
		
		inputBox = new InputBox(true, false, 800, 250);
		inputBox.addListener(this);
		inputBox.setVisible(true);
	}

	@Override
	public void printUsage() 
	{
		
	}

	@Override
	public void printDescription() 
	{
		
	}
	
	@Override
	public void OnEnter(char[] password) {}

	@Override
	public void OnShiftEnter(char[] text) 
	{
		inputBox.close();
	}

	@Override
	public void OnCtrlEnter(char[] text) 
	{
		inputBox.clear();
	}
	
	private void encryptTextToLog(char[] text)
	{
		try 
		{
			//generate an aes key
			KeyGenerator keyGen = KeyGenerator.getInstance("AES");
			keyGen.init(256); // for example
			SecretKey aesKey = keyGen.generateKey();
			
			//use it to encrypt the text
			Cipher aesCipher = Cipher.getInstance("AES");
			aesCipher.init(Cipher.ENCRYPT_MODE, aesKey);
            byte[] cipherText = aesCipher.doFinal(new String(text).getBytes("UTF-8"));

			//encrypt the aes key with the public rsa key
            Cipher rsaCipher = Cipher.getInstance("RSA");
            SecretKey publicRsaKey = new SecretKeySpec(Base64.decodeBase64(Settings.instance().getValue("publickey")), "RSA");
            rsaCipher.init(Cipher.ENCRYPT_MODE, publicRsaKey);
            byte[] encryptedAesKey = rsaCipher.doFinal(aesKey.getEncoded());
            
			//generate length codes
            byte[] lengthCode1 = ByteBuffer.allocate(4).putInt(encryptedAesKey.length).array();
            byte[] lengthCode2 = ByteBuffer.allocate(4).putInt(cipherText.length).array();
            
            //create an output byte array
            ByteBuffer output = ByteBuffer.allocate(lengthCode1.length + encryptedAesKey.length + lengthCode2.length + cipherText.length);
            
			//add the first length code to the output
           	output.put(lengthCode1);
            
			//add the encrypted aes key to the byte block
           	output.put(encryptedAesKey);
           	
           	//add the second length code
           	output.put(lengthCode2);
           	
            //add the encrypted text to the byte block
           	output.put(cipherText);
           	
           	//TODO append the byte block to the current log
		} 
		catch (NoSuchAlgorithmException e) 
		{
			System.err.println(e.getMessage());
		} 
		catch (NoSuchPaddingException e) 
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
		catch (UnsupportedEncodingException e) 
		{
			System.err.println(e.getMessage());
		} 
		catch (InvalidKeyException e) 
		{
			System.err.println(e.getMessage());
		} 
		catch (CorruptSettingsException e) 
		{
			System.err.println(e.getMessage());
		} 
		catch (ParserConfigurationException e) 
		{
			System.err.println(e.getMessage());
		} 
		catch (SAXException e) 
		{
			System.err.println(e.getMessage());
		} 
		catch (IOException e) 
		{
			System.err.println(e.getMessage());
		}
	}

	@Override
	public void OnEscape()
	{
		inputBox.close();
	}
	
}
