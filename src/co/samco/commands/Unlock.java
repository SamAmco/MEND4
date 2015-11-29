package co.samco.commands;

import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPasswordField;
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

import co.samco.mend.ColorSchemeData;

public class Unlock extends Command implements InputBoxListener
{
	@Override
	public void execute(ArrayList<String> args) 
	{
		InputBox inputBox = new InputBox();
		inputBox.addListener(this);
		inputBox.setVisible(true);
	}
	
	@Override
	public void printUsage() 
	{
		super.printUsage();
		System.err.println("mend unlock");
	}

	@Override
	public void OnEnter(char[] password) 
	{
		try 
		{
			//If there is already a prKey.dex file existent, just shred it and unlock again.
			File existingKey = new File("prKey.dec");
			if (existingKey.exists())
			{
				System.out.println("Removing existing prKey.dec");
				System.out.println();
				Process tr = Runtime.getRuntime().exec(new String[]{"shred", "-u", "prKey.dec"});
				BufferedReader rd = new BufferedReader(new InputStreamReader(tr.getInputStream()));
				String s = rd.readLine();
				while (s != null)
				{
					System.out.println(s);
					s = rd.readLine();
				}
			}
			
			//Check that the user has an options file.
			File f = new File("Settings.xml");
			if (f.exists())
			{
				//Check that the options file contains a password hash.
				DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder dBuilder;
				dBuilder = dbFactory.newDocumentBuilder();
				
				Document doc = dBuilder.parse(f);
				doc.getDocumentElement().normalize();

				//Check that the user has an encrypted private key.
				NodeList nList = doc.getElementsByTagName("passhash");
				
				if (nList.getLength() <= 0)
				{
					System.err.println("Could not find your passhash, please ensure your options file is configured correctly.");
					return;
				}
				else if(nList.getLength() > 1)
				{
					System.err.println("Invalid Options file (contains multiple definitions for 'passhash').");
					return;
				}
				else
				{
					Node node = nList.item(0);
					if (node.getNodeType() == Node.ELEMENT_NODE)
					{
						Element el = (Element)node;
						String hash = el.getAttribute("value");
						
						//Check that the users entered password hash matches the stored hash.
						byte[] bytesOfFileHash = Base64.decodeBase64(hash);

						MessageDigest md = MessageDigest.getInstance("MD5");
						String passwordString = new String(password);
						byte[] inputDigest = md.digest(passwordString.getBytes());
						
						if (Arrays.equals(bytesOfFileHash, inputDigest))
						{
							//Decrypt the private key with the password.
							MessageDigest sha = MessageDigest.getInstance("SHA-1");
					        byte[] key = sha.digest(passwordString.getBytes());
					        
					        SecretKeySpec secretKeySpec = new SecretKeySpec(Arrays.copyOf(key, 16), "AES");
					        Cipher cipher = Cipher.getInstance("AES");
					        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
					        
					        byte[] encryptedPrivateKey = IOUtils.toByteArray(new FileInputStream(new File("prKey.enc")));
					        byte[] decryptedPrivateKey = cipher.doFinal(encryptedPrivateKey);
					        
					        //Write the decrypted private key to a file
					        File privateKeyFile = new File("prKey.dec");
					        FileOutputStream fos = new FileOutputStream(privateKeyFile);
					        fos.write(decryptedPrivateKey);
					        fos.flush();
					        fos.close();
							
							System.out.println("MEND Unlocked.");
						}
						else
						{
							System.err.println("Unlock Failed: Incorrect password.");
						}
					}
					else
					{
						System.err.println("Invalid Options file. passhash is not a valid element node.");
					}
				}
			}
			else
			{
				System.err.println("Could not find Settings.xml");
			}
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
	
	private class InputBox extends JFrame implements KeyListener
	{
		private static final long serialVersionUID = -7214084221385969252L;
		
		JPasswordField passwordField;
		List<InputBoxListener> listeners = new ArrayList<InputBoxListener>();
		
		public InputBox()
		{
			setSize(200,25);
			this.setUndecorated(true);
			setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
			
			passwordField = new JPasswordField();
			passwordField.setPreferredSize(new Dimension(50, 25));
			passwordField.setBackground(ColorSchemeData.getColor2());
			passwordField.setFont(ColorSchemeData.getConsoleFont());
			passwordField.setBorder(BorderFactory.createLineBorder(ColorSchemeData.getColor1()));
			passwordField.addKeyListener(this);
			this.add(passwordField);
			
		}
		
		public void addListener(InputBoxListener l)
		{
			listeners.add(l);
		}

		@Override
		public void keyPressed(KeyEvent arg0) {}

		@Override
		public void keyReleased(KeyEvent e) 
		{
			if (e.getKeyCode() == KeyEvent.VK_ENTER)
			{
				for (InputBoxListener l : listeners)
					l.OnEnter(passwordField.getPassword());
				
				dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
			}
		}

		@Override
		public void keyTyped(KeyEvent e) {}
	}

}
