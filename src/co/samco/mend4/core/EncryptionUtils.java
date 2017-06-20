package co.samco.mend4.core;

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
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import co.samco.mend4.core.Settings;
import co.samco.mend4.core.Settings.CorruptSettingsException;
import co.samco.mend4.core.Settings.InvalidSettingNameException;
import co.samco.mend4.core.Settings.UnInitializedSettingsException;

public class EncryptionUtils 
{
	private static String addHeaderToLogText(char[] logText) throws UnInitializedSettingsException
	{
		StringBuilder sb = new StringBuilder();
		Calendar cal = Calendar.getInstance();
	    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ENGLISH);
	   	sb.append(sdf.format(cal.getTime()));
	   	sb.append("//MEND"+Config.CORE_VERSION_NUMBER+"//");
	   	sb.append(Settings.instance().getPlatformDependentHeader());
	   	sb.append("////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////\n");
	   	sb.append(logText);
	    return sb.toString();
	}
	
	public static void encryptFileToStream(IBase64EncodingProvider encoder, FileInputStream fis, FileOutputStream fos, String fileExtension) 
			throws CorruptSettingsException, InvalidSettingNameException, UnInitializedSettingsException, MissingPublicKeyException, 
			NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, InvalidKeySpecException, 
			IllegalBlockSizeException, BadPaddingException, IOException
	{
		CipherOutputStream cos = null;
		try
		{
			String userPublicKeyString = Settings.instance().getValue(Config.Settings.PUBLICKEY);
			if (userPublicKeyString == null)
				throw new MissingPublicKeyException();
			
			//generate an aes key
			KeyGenerator keyGen = KeyGenerator.getInstance("AES");
			keyGen.init(Config.AES_KEY_SIZE());
			SecretKey aesKey = keyGen.generateKey();

			Cipher aesCipher = Cipher.getInstance(Config.PREFERRED_AES_ALG());
			aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, Config.STANDARD_IV);
			cos = new CipherOutputStream(fos, aesCipher);
			
			//encrypt the aes key with the public rsa key
            Cipher rsaCipher = Cipher.getInstance(Config.PREFERRED_RSA_ALG());
            
            //read in the public key
            X509EncodedKeySpec publicRsaKeySpec = new X509EncodedKeySpec(encoder.decodeBase64(userPublicKeyString));
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
           	
           	byte[] fileExtensionBytes = rsaCipher.doFinal(fileExtension.getBytes());
           	fos.write(ByteBuffer.allocate(4).putInt(fileExtensionBytes.length).array());
           	fos.write(fileExtensionBytes);
           	
           	byte[] buffer = new byte[8192];
           	int count;
           	while ((count = fis.read(buffer)) > 0)
           	{
           	    cos.write(buffer, 0, count);
           	}
		}
		finally
		{
			if (cos != null)
				cos.close();
		}
	}
	
	public static void encryptLogToStream(IBase64EncodingProvider encoder, FileOutputStream fos, char[] text, boolean dropHeader) 
			throws CorruptSettingsException, InvalidSettingNameException, UnInitializedSettingsException,
		NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException,
		IllegalBlockSizeException, BadPaddingException, InvalidKeySpecException, MissingPublicKeyException, IOException
	{
		//Lets just do some basic checks first
		String userPublicKeyString = Settings.instance().getValue(Config.Settings.PUBLICKEY);
		if (userPublicKeyString == null)
			throw new MissingPublicKeyException();
        
		//generate an aes key
		KeyGenerator keyGen = KeyGenerator.getInstance("AES");
		keyGen.init(Config.AES_KEY_SIZE());
		SecretKey aesKey = keyGen.generateKey();
		
		//use it to encrypt the text
		Cipher aesCipher = Cipher.getInstance(Config.PREFERRED_AES_ALG());
		aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, Config.STANDARD_IV);
		String logText;
		if (dropHeader)
			logText = new String(text);
		else 
			logText = addHeaderToLogText(text);
        byte[] cipherText = aesCipher.doFinal(logText.getBytes("UTF-8"));

		//encrypt the aes key with the public rsa key
        Cipher rsaCipher = Cipher.getInstance(Config.PREFERRED_RSA_ALG());
        
        //read in the public key
        X509EncodedKeySpec publicRsaKeySpec = new X509EncodedKeySpec(encoder.decodeBase64(userPublicKeyString));
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
       	fos.write(output.array());
	}
	
	public static class MissingPublicKeyException extends Exception
	{
		private static final long serialVersionUID = 8041634883751009836L;

		public MissingPublicKeyException()
		{
			super("");
		}
	}
}





