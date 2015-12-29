package co.samco.commands;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.apache.commons.codec.binary.Base64;
import org.xml.sax.SAXException;

import co.samco.mend.Command;
import co.samco.mend.Config;
import co.samco.mend.InputBox;
import co.samco.mend.InputBoxListener;
import co.samco.mend.Settings;
import co.samco.mend.Settings.CorruptSettingsException;
import co.samco.mend.Settings.InvalidSettingNameException;

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
		inputBox.clear();
		encryptTextToLog(text);
		inputBox.close();
	}

	@Override
	public void OnCtrlEnter(char[] text) 
	{
		inputBox.clear();
		encryptTextToLog(text);
	}
	
	private void encryptTextToLog(char[] text)
	{
		try 
		{
			//Lets just do some basic checks first
			String userPublicKeyString = Settings.instance().getValue(Config.Settings.PUBLICKEY);
            if (userPublicKeyString == null)
            {
            	System.err.println("Failed to find your public key. Please ensure you have run \"mend setup\" "
            			+ "and that your Settings are not corrupt or in-accessable to mend");
            	return;
            }
            String currentLogName = Settings.instance().getValue(Config.Settings.CURRENTLOG);
           	if (currentLogName == null)
           	{
           		Settings.instance().setValue(Config.Settings.CURRENTLOG, "Log.mend");
           		currentLogName = "Log.mend";
           	}
            
			//generate an aes key
			KeyGenerator keyGen = KeyGenerator.getInstance("AES");
			keyGen.init(Config.AES_KEY_SIZE);
			SecretKey aesKey = keyGen.generateKey();
			
			//use it to encrypt the text
			Cipher aesCipher = Cipher.getInstance(Config.PREFERRED_AES_ALG);
			aesCipher.init(Cipher.ENCRYPT_MODE, aesKey);
            byte[] cipherText = aesCipher.doFinal(new String(text).getBytes("UTF-8"));

			//encrypt the aes key with the public rsa key
            Cipher rsaCipher = Cipher.getInstance(Config.PREFERRED_RSA_ALG);
            
            X509EncodedKeySpec publicRsaKeySpec = new X509EncodedKeySpec(Base64.decodeBase64(userPublicKeyString));
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicRsaKey = keyFactory.generatePublic(publicRsaKeySpec);
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
           	
           	//append the byte block to the current log
           	File currentLogFile = new File(Config.CONFIG_PATH + currentLogName);
       		currentLogFile.createNewFile();
       		
       		FileOutputStream outFile = new FileOutputStream(currentLogFile, true);
       		try 
       		{
       			outFile.write(output.array());
       		}
       		finally 
       		{
       			outFile.close();
       			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
       			Date date = new Date();
       			System.out.println("Successfully Logged entry at: " + dateFormat.format(date));
       		}
		} 
		catch (NoSuchAlgorithmException | IOException | InvalidKeyException 
				| IllegalBlockSizeException | BadPaddingException 
				| InvalidKeySpecException | TransformerException | CorruptSettingsException 
				| InvalidSettingNameException | ParserConfigurationException 
				| SAXException | NoSuchPaddingException e) 
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
