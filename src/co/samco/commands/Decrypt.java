package co.samco.commands;

import java.io.File;
import java.io.FileInputStream;
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
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import co.samco.mend.Command;
import co.samco.mend.Config;

public class Decrypt extends Command
{
	@Override
	public void execute(ArrayList<String> args) 
	{
		super.execute(args);
		if(!continueExecution)
			return;
		
		//TODO allow the decryption of files.
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
			
			//make sure mend is unlocked
			File privateKeyFile = new File(Config.CONFIG_PATH + Config.PRIVATE_KEY_FILE_DEC);
			if (!privateKeyFile.exists())
			{
				System.err.println("MEND is Locked. Please run mend unlock");
				return;
			}
			
			//Check the file they specified actually exists
			File file = new File(args.get(0));
			String filename = file.getName();
			if (!file.exists())
			{
				System.err.println("Could not find specified file.");
				return;
			}
			
			//now read in the private rsa key.
	        byte[] keyBytes = new byte[(int)privateKeyFile.length()];
	        privateKeyFileInputStream = new FileInputStream(privateKeyFile);
	        privateKeyFileInputStream.read(keyBytes);
			PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(keyBytes);
	        KeyFactory kf = KeyFactory.getInstance("RSA");
	        RSAPrivateKey privateKey = (RSAPrivateKey)kf.generatePrivate(privateKeySpec);
			
			//TODO you could make the file extentions configurable, what if people already have software installed that uses one or both of these file extentions?
			
			//if it's a log file decrypt it as a log
			if (filename.substring(filename.lastIndexOf(".") + 1, filename.length()).equals("mend"))
			{
				decryptLog(file, privateKey);
			}
			//if it's just an encrypted file decrypt it as that.
			else if (filename.substring(filename.lastIndexOf(".") + 1, filename.length()).equals("enc"))
			{
				decryptFile(file);
			}
			//if the file extention was not recognized 
			else 
			{
				System.err.println("MEND does not know how to decrypt this file as it does not recognize the file extention. Expecting either .mend or .enc");
			}
		} 
		catch (NoSuchAlgorithmException | IOException | InvalidKeySpecException e) 
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
	
	
	private void decryptFile(File file)
	{
		
	}
	
	@Override
	public String getUsageText() 
	{
		return "Usage:\tmend dec <log_file>";
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
