package co.samco.mend4.commands;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.FilenameUtils;
import org.xml.sax.SAXException;

import co.samco.mend4.desktop.Config;
import co.samco.mend4.desktop.Settings;
import co.samco.mend4.desktop.Settings.CorruptSettingsException;
import co.samco.mend4.desktop.Settings.InvalidSettingNameException;

public class Decrypt extends Command
{
	@Override
	public void execute(ArrayList<String> args) 
	{
		if (printHelp(args))
			return;
		
		//TODO allow them to specify an output file.. or specify that the file should be written to the dec directory
		FileInputStream privateKeyFileInputStream = null;
		try 
		{
			//check they provided a file to decrypt
			if (args.size() < 1)
			{
				System.err.println("Please provide the file to decrypt.");
				System.err.println(getUsageText());
				return;
			}
			
			boolean silent = false;
			if (args.contains("-s"))
			{
				silent = true;
				args.remove("-s");
			}
			
			//make sure mend is unlocked
			File privateKeyFile = new File(Config.CONFIG_PATH + Config.PRIVATE_KEY_FILE_DEC);
			if (!privateKeyFile.exists())
			{
				System.err.println("MEND is Locked. Please run mend unlock");
				return;
			}
			
			//First check the special case that it's a 17 or 14 digit enc file id
			String filePath = args.get(0);
			if (filePath.matches("\\d{14}") || filePath.matches("\\d{16}") || filePath.matches("\\d{17}"))
			{
				String encDir = Settings.instance().getValue(Config.Settings.ENCDIR);
				if (encDir == null)
				{
					System.err.println("You need to set the " + Config.SETTINGS_NAMES_MAP.get(Config.Settings.ENCDIR.ordinal())
							+ " property in your settings file before you can decrypt files from it.");
					return;
				}
				filePath = encDir + File.separatorChar + filePath + ".enc";
			}
			
			
			File file = new File(filePath);
			if (!file.exists() || !file.isFile())
			{
				//Then check it's the case where it's a logfile name
				String extension = FilenameUtils.getExtension(filePath);
				if (extension.equals(""))
					filePath += ".mend";

				String logDir = Settings.instance().getValue(Config.Settings.LOGDIR);
				if (logDir == null)
				{
					System.err.println("Defaulted to searching " 
							+ Config.SETTINGS_NAMES_MAP.get(Config.Settings.LOGDIR.ordinal()) 
							+ " but the property is not set in your Settings file.");
					return;
				}

				file = new File(logDir + File.separatorChar + filePath);
				if (!file.exists() || !file.isFile())
				{
					System.err.println("Could not find specified file: " + file.getAbsolutePath());
					return;
				}
			}
			String filename = file.getName();
			
			//now read in the private rsa key.
	        byte[] keyBytes = new byte[(int)privateKeyFile.length()];
	        privateKeyFileInputStream = new FileInputStream(privateKeyFile);
	        privateKeyFileInputStream.read(keyBytes);
			PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(keyBytes);
	        KeyFactory kf = KeyFactory.getInstance("RSA");
	        RSAPrivateKey privateKey = (RSAPrivateKey)kf.generatePrivate(privateKeySpec);
			
			//TODO you could make the file extentions configurable, what if people already have software installed that uses one or both of these file extentions?
			
			//if it's a log file decrypt it as a log
			if (FilenameUtils.getExtension(filename).equals("mend"))
				decryptLog(file, privateKey);
			//if it's just an encrypted file decrypt it as that.
			else if (FilenameUtils.getExtension(filename).equals("enc"))
				decryptFile(file, privateKey, silent);
			//if the file extention was not recognized 
			else 
			{
				System.err.println("Failed attempting to decrypt: " + filename);
				System.err.println("MEND does not know how to decrypt this file as it does not recognize the file extention. Expecting either .mend or .enc");
			}
		} 
		catch (NoSuchAlgorithmException | IOException | InvalidKeySpecException 
				| CorruptSettingsException | InvalidSettingNameException 
				| ParserConfigurationException | SAXException e) 
		{
			System.err.println(e.getMessage());
		} 
		finally
		{
			try 
			{
				if (privateKeyFileInputStream != null)
					privateKeyFileInputStream.close();
			} 
			catch (IOException e) 
			{
				System.err.println(e.getMessage());
			}
		}
	}
	
	private void decryptLog(File file, RSAPrivateKey privateKey)
	{
		FileInputStream inputStream = null;
		try
		{
			inputStream = new FileInputStream(file);
			byte[] lc1Bytes = new byte[4];
			//while there is an initial length code to be read in, read it in
			while (inputStream.read(lc1Bytes) == 4)
			{
				//then convert it to an integer
				ByteBuffer lc1Buff = ByteBuffer.wrap(lc1Bytes); //big-endian by default
				int lc1 = lc1Buff.getInt();
				
				//and read in that many bytes as the encrypted aes key used to encrypt this log entry
				byte[] encAesKey = new byte[lc1];
				if (inputStream.read(encAesKey) != encAesKey.length)
				{
					throw new MalformedLogFileException("This log file is malformed.");
				}
				
				//now decrypt the aes key
				Cipher rsaCipher = Cipher.getInstance(Config.PREFERRED_RSA_ALG);
	            rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
	            byte[] aesKeyBytes = rsaCipher.doFinal(encAesKey);
	            SecretKey aesKey = new SecretKeySpec(aesKeyBytes, 0, aesKeyBytes.length, "AES");

	            //read in the next length code
	            byte[] lc2Bytes = new byte[4];
	            if (inputStream.read(lc2Bytes) != 4)
	            {
	            	throw new MalformedLogFileException("This log file is malformed.");
	            }
	            
	            //convert that to an integer
	            ByteBuffer lc2Buff = ByteBuffer.wrap(lc2Bytes); //big-endian by default
				int lc2 = lc2Buff.getInt();
				
				//now we can read in that many bytes as the encrypted log data
				byte[] encEntry = new byte[lc2];
				if (inputStream.read(encEntry) != encEntry.length)
				{
					throw new MalformedLogFileException("This log file is malformed.");
				}
	            
				//now decrypt the entry
				Cipher aesCipher = Cipher.getInstance(Config.PREFERRED_AES_ALG);
				aesCipher.init(Cipher.DECRYPT_MODE, aesKey, Config.STANDARD_IV);
	            byte[] entry = aesCipher.doFinal(encEntry);
	            
	            System.out.println(new String(entry, "UTF-8"));
	            System.out.println();
			}
		}
		catch(IOException | MalformedLogFileException | NoSuchAlgorithmException 
				| NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException 
				| BadPaddingException | InvalidAlgorithmParameterException e)
		{
			System.err.println(e.getMessage());
		}
		finally
		{
			try 
			{
				if (inputStream != null)
					inputStream.close();
			} 
			catch (IOException e) 
			{
				System.err.println(e.getMessage());
			}
		}
	}
	
	
	private void decryptFile(File file, RSAPrivateKey privateKey, boolean silent)
	{
		FileInputStream fis = null;
		FileOutputStream fos = null;
		CipherOutputStream cos = null;
		try
		{
			String decLocation = Settings.instance().getValue(Config.Settings.DECDIR);
			if (decLocation == null)
				throw new FileNotFoundException("You need to set the " + Config.SETTINGS_NAMES_MAP.get(Config.Settings.DECDIR.ordinal())
						+ " property in your settings file before you can decrypt files to it.");
			
			decLocation += File.separatorChar;
			
			fis = new FileInputStream(file);
			byte[] lcBytes = new byte[4];
			//while there is an initial length code to be read in, read it in
			if (fis.read(lcBytes) != lcBytes.length)
				throw new MalformedLogFileException("This enc file is malformed");
			
			//then convert it to an integer
			int lc = ByteBuffer.wrap(lcBytes).getInt(); //big-endian by default
			
			//and read in that many bytes as the encrypted aes key used to encrypt this enc file
			byte[] encAesKey = new byte[lc];
			if (fis.read(encAesKey) != encAesKey.length)
				throw new MalformedLogFileException("This log file is malformed.");
			
			//now decrypt the aes key
			Cipher rsaCipher = Cipher.getInstance(Config.PREFERRED_RSA_ALG);
            rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] aesKeyBytes = rsaCipher.doFinal(encAesKey);
            SecretKey aesKey = new SecretKeySpec(aesKeyBytes, 0, aesKeyBytes.length, "AES");
			
           	//first get the file extension
           	byte[] lc2Bytes = new byte[4];
           	
           	if (fis.read(lc2Bytes) != lc2Bytes.length)
           		throw new MalformedLogFileException("This enc file is malformed");
           	
           	int lc2 = ByteBuffer.wrap(lc2Bytes).getInt(); //big-endian by default
           	byte[] encFileExtension = new byte[lc2];
           	
           	if (fis.read(encFileExtension) != encFileExtension.length)
           		throw new MalformedLogFileException("This enc file is malformed");

           	String fileExtension = new String(rsaCipher.doFinal(encFileExtension));
           	
           	File outputFile = new File(decLocation + FilenameUtils.removeExtension(file.getName()) + '.' + fileExtension);
			if (outputFile.getParentFile() == null || !outputFile.getParentFile().exists())
				throw new FileNotFoundException("Could not write to the decrypt location.");
			
			if (outputFile.exists())
			{
				System.err.println("The output file already exists at: " + outputFile.getAbsolutePath());
				return;
			}
			
			fos = new FileOutputStream(outputFile);
			
			//now decrypt the file
			Cipher aesCipher = Cipher.getInstance(Config.PREFERRED_AES_ALG);
			aesCipher.init(Cipher.DECRYPT_MODE, aesKey, Config.STANDARD_IV);
			cos = new CipherOutputStream(fos, aesCipher);
           	
           	System.err.println("Decrypting the file to: " + outputFile.getAbsolutePath());
           	//now we can append all the decrypted file bytes
           	byte[] buffer = new byte[8192];
           	int count;
           	while ((count = fis.read(buffer)) > 0)
           	{
           	    cos.write(buffer, 0, count);
           	}
           	System.err.println("Decryption complete.");
           	
           	if (!silent)
           		Desktop.getDesktop().open(outputFile);
		}
		catch(IOException | MalformedLogFileException | NoSuchAlgorithmException 
				| NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException 
				| BadPaddingException | InvalidAlgorithmParameterException | CorruptSettingsException 
				| InvalidSettingNameException | ParserConfigurationException | SAXException e)
		{
			System.err.println(e.getMessage());
		}
		finally
		{
			try 
			{
				if (fis != null)
					fis.close();
				if (fos != null)
					fos.close();
				if (cos != null)
					cos.close();
			} 
			catch (IOException e) 
			{
				System.err.println(e.getMessage());
			}
		}
	}
	
	@Override
	public String getUsageText() 
	{
		return "Usage:\tmend dec [-s] <log_file>|<enc_file>";
	}

	@Override
	public String getDescriptionText() 
	{
		return "To decrypt an encrypted log or other mend encrypted file.";
	}
	
	
	private static class MalformedLogFileException extends Exception
	{
		private static final long serialVersionUID = 9219333934024822210L;

		public MalformedLogFileException(String message)
		{
			super(message);
		}
	}
	
}
