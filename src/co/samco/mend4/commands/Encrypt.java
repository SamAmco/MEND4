package co.samco.mend4.commands;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FilenameUtils;
import org.xml.sax.SAXException;

import co.samco.mend4.desktop.Config;
import co.samco.mend4.desktop.InputBox;
import co.samco.mend4.desktop.InputBoxListener;
import co.samco.mend4.desktop.Settings;
import co.samco.mend4.desktop.Settings.CorruptSettingsException;
import co.samco.mend4.desktop.Settings.InvalidSettingNameException;

public class Encrypt extends Command implements InputBoxListener
{
	private InputBox inputBox;
	protected boolean dropHeader = false;

	@Override
	public void execute(ArrayList<String> args) 
	{
		if (printHelp(args))
			return;
		
		readOptions(args);
		
		if (args.size() <= 0)
		{
			inputBox = new InputBox(true, false, 800, 250);
			inputBox.addListener(this);
			inputBox.setVisible(true);
		}
		else if (args.size() > 0)
		{
			if (args.contains("-d"))
			{
				int index = args.indexOf("-d");
				if (args.size() < index + 2)
				{
					System.err.println("You must provide the text you wish to encrypt after the -d option");
					return;
				}
				encryptTextToLog(args.get(index + 1).toCharArray(), dropHeader);
				return;
			}
			
			if (args.size() > 2)
				System.err.println("Invalid number of arguments. See \"mend enc -h\" for more information.");

			String name = null;
			if (args.size() > 1)
				name = args.get(1);
				
			encryptFile(args.get(0), name);
			
			return;
		}
	}
	
	protected void readOptions(ArrayList<String> args)
	{
		if (args.contains("-a"))
		{
			dropHeader = true;
			args.remove("-a");
		}
	}

	@Override
	public String getUsageText() 
	{
		return "Usage:\tmend enc [-a][-d <text>|<path to file> [<encrypted file name>]]";
	}

	@Override
	public String getDescriptionText() 
	{
		return "To encrypt text to your current log, or encrypt a file and recieve an id for it.";
	}
	
	@Override
	public void OnEnter(char[] password) {}

	@Override
	public void OnShiftEnter(char[] text) 
	{
		inputBox.clear();
		encryptTextToLog(text, dropHeader);
		inputBox.close();
	}

	@Override
	public void OnCtrlEnter(char[] text) 
	{
		inputBox.clear();
		encryptTextToLog(text, dropHeader);
	}
	
	@SuppressWarnings("resource")
	private void encryptFile(String filePath, String name)
	{
		//If there isn't a file name given, then let's generate a name for the file using a timestamp (16 digits).
		if (name == null)
			name = new SimpleDateFormat("yyyyMMddHHmmssSS").format(new Date());
		
		File fileToEncrypt = new File(filePath);
		if (!fileToEncrypt.exists())
		{
			System.err.println("Could not find file.");
			return;
		}
		
		FileInputStream fis = null;
		FileOutputStream fos = null;
		CipherOutputStream cos = null;
		
		try
		{
			String userPublicKeyString = getUserPublicKeyString();
			
			//Make sure you're able to set up a file input stream
			fis = new FileInputStream(fileToEncrypt);
			
			//Check that you're able to set up an output stream
			String encLocation = Settings.instance().getValue(Config.Settings.ENCDIR);
			if (encLocation == null)
			{
				throw new IOException("You need to set the " + Config.SETTINGS_NAMES_MAP.get(Config.Settings.ENCDIR.ordinal()) 
							+ " property in your settings file before you can encrypt files to it.");
			}
			File outputFile = new File(encLocation + File.separatorChar + name + ".enc");
			if (outputFile.exists())
			{
				System.err.println("The output file already exists: " + outputFile.getAbsolutePath());
			}
			fos = new FileOutputStream(outputFile);
			
			//generate an aes key
			KeyGenerator keyGen = KeyGenerator.getInstance("AES");
			keyGen.init(Config.AES_KEY_SIZE);
			SecretKey aesKey = keyGen.generateKey();

			Cipher aesCipher = Cipher.getInstance(Config.PREFERRED_AES_ALG);
			aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, Config.STANDARD_IV);
			cos = new CipherOutputStream(fos, aesCipher);
			
			//encrypt the aes key with the public rsa key
            Cipher rsaCipher = Cipher.getInstance(Config.PREFERRED_RSA_ALG);
            
            //read in the public key
            X509EncodedKeySpec publicRsaKeySpec = new X509EncodedKeySpec(Base64.decodeBase64(userPublicKeyString));
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicRsaKey = keyFactory.generatePublic(publicRsaKeySpec);
            rsaCipher.init(Cipher.ENCRYPT_MODE, publicRsaKey);
            byte[] encryptedAesKey = rsaCipher.doFinal(aesKey.getEncoded());
            
            //generate length codes
            byte[] lengthCode1 = ByteBuffer.allocate(4).putInt(encryptedAesKey.length).array();
            
            //create an output byte array
            ByteBuffer output = ByteBuffer.allocate(lengthCode1.length + encryptedAesKey.length);
            
			//add the first length code to the output
           	output.put(lengthCode1);
            
			//add the encrypted aes key to the byte block
           	output.put(encryptedAesKey);
           	
           	//write the key length and encrypted key to the file
           	fos.write(output.array());
           	
           	//now we can append all the encrypted file bytes
           	System.err.println("Encrypting file to: " + outputFile.getAbsolutePath());
           	
           	String fileExtension = FilenameUtils.getExtension(fileToEncrypt.getAbsolutePath());
           	byte[] fileExtensionBytes = rsaCipher.doFinal(fileExtension.getBytes());
           	fos.write(ByteBuffer.allocate(4).putInt(fileExtensionBytes.length).array());
           	fos.write(fileExtensionBytes);
           	
           	byte[] buffer = new byte[8192];
           	int count;
           	while ((count = fis.read(buffer)) > 0)
           	{
           	    cos.write(buffer, 0, count);
           	}
           	System.err.println("Encryption complete. Key: " + name);
		}
		catch ( IOException | CorruptSettingsException 
				| InvalidSettingNameException | ParserConfigurationException 
				| SAXException | NoSuchPaddingException | InvalidAlgorithmParameterException 
				| NoSuchAlgorithmException | InvalidKeyException | InvalidKeySpecException 
				| IllegalBlockSizeException | BadPaddingException e) 
		{
			System.err.println(e.getMessage());
			try 
			{
				if (fis != null)
					fis.close();
				if (fos != null)
					fos.close();
				if (cos != null)
					cos.close();
			} 
			catch (IOException e1) 
			{
				System.err.println("MEND encountered a fatal error. Some file streams may not have been closed.");
				e1.printStackTrace();
			}
		}
	}
	
	private String getUserPublicKeyString() throws CorruptSettingsException, InvalidSettingNameException, ParserConfigurationException, SAXException, IOException
	{
		String userPublicKeyString = Settings.instance().getValue(Config.Settings.PUBLICKEY);
        if (userPublicKeyString == null)
        {
        	System.err.println("Failed to find your public key. Please ensure you have run \"mend setup\" "
        			+ "and that your Settings are not corrupt or in-accessable to mend");
        }
        return userPublicKeyString;
	}
	
	private String addHeaderToLogText(char[] logText)
	{
		StringBuilder sb = new StringBuilder();
		Calendar cal = Calendar.getInstance();
	    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ENGLISH);
	   	sb.append(sdf.format(cal.getTime()));
	   	sb.append("//MEND"+Config.VERSION_NUMBER);
	   	sb.append("//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////\n");
	   	sb.append(logText);
	    return sb.toString();
	}
	
	protected void encryptTextToLog(char[] text, boolean dropHeader)
	{
		if (text.length <= 0)
			return;
		
		FileOutputStream outFile = null;
		try 
		{
			//Lets just do some basic checks first
			String userPublicKeyString = getUserPublicKeyString();
			if (userPublicKeyString == null)
				return;
            String currentLogName = Settings.instance().getValue(Config.Settings.CURRENTLOG);
            String logDir = Settings.instance().getValue(Config.Settings.LOGDIR);
            if (logDir == null)
            	throw new IOException("You need to set the " + Config.SETTINGS_NAMES_MAP.get(Config.Settings.LOGDIR.ordinal()) 
					+ " property in your settings file before you can encrypt logs to it.");
           	if (currentLogName == null)
           	{
           		Settings.instance().setValue(Config.Settings.CURRENTLOG, "Log");
           		currentLogName = "Log";
           	}
            
			//generate an aes key
			KeyGenerator keyGen = KeyGenerator.getInstance("AES");
			keyGen.init(Config.AES_KEY_SIZE);
			SecretKey aesKey = keyGen.generateKey();
			
			//use it to encrypt the text
			Cipher aesCipher = Cipher.getInstance(Config.PREFERRED_AES_ALG);
			aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, Config.STANDARD_IV);
			String logText;
			if (dropHeader)
				logText = new String(text);
			else 
				logText = addHeaderToLogText(text);
            byte[] cipherText = aesCipher.doFinal(logText.getBytes("UTF-8"));

			//encrypt the aes key with the public rsa key
            Cipher rsaCipher = Cipher.getInstance(Config.PREFERRED_RSA_ALG);
            
            //read in the public key
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
           	if (currentLogName.length() < 1)
           		currentLogName = "Log";
           	if (currentLogName.length() < 6 || !currentLogName.substring(currentLogName.length() - 5, currentLogName.length()).equals(".mend"))
				currentLogName += ".mend";
           	File currentLogFile = new File(logDir + File.separatorChar + currentLogName);
       		currentLogFile.createNewFile();
       		
       		outFile = new FileOutputStream(currentLogFile, true);
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
				| SAXException | NoSuchPaddingException | InvalidAlgorithmParameterException e) 
		{
			System.err.println(e.getMessage());
			try 
			{
				if (outFile != null)
					outFile.close();
			}
			catch (IOException e1) 
			{
				System.err.println("MEND encountered a fatal error. Some file streams may not have been closed.");
				e1.printStackTrace();
			}
		} 
	}

	@Override
	public void OnEscape()
	{
		inputBox.close();
	}
	
}
