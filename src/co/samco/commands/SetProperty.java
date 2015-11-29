package co.samco.commands;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import co.samco.mend.Command;

public class SetProperty extends Command
{
	@Override
	public void execute(ArrayList<String> args) 
	{
		if (args.size() != 2)
		{
			System.err.println("Wrong number of arguments:");
			System.err.println();
			printUsage();
			return;
		}
		
		String propertyName = args.get(0);
		String value = args.get(1);
		
		File settingsFile = new File(CONFIG_PATH + SETTINGS_FILE);
		if (!settingsFile.exists())
		{
			System.err.println("Could not find " + SETTINGS_FILE + " at:");
			System.err.println(settingsFile.getAbsolutePath());
			System.err.println("Please make sure MEND has been set up correctly.");
			return;
		}
		
		if (propertyName.equals("logDir") || propertyName.equals("encDir") 
				|| propertyName.equals("decDir"))
		{
			try
			{
				setProperty(settingsFile, propertyName, value);
			}
			catch (ParserConfigurationException | SAXException | IOException | TransformerException e)
			{
				System.err.println(e.getMessage());
			}
		}
		else
		{
			System.err.println(propertyName + " is not a recognised property name.");
			System.err.println();
			printUsage();
		}
		
	}
	
	/**Access the settings file and update it.
	 * @throws TransformerException 
	 */
	private void setProperty(File settingsFile, String property, String value) 
			throws ParserConfigurationException, SAXException, IOException, TransformerException
	{
		//Parse the settings file.
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
		Document doc = docBuilder.parse(settingsFile.getAbsolutePath());
				
		//Find the root of the document
		Node rootElement = doc.getFirstChild();
		
		if (rootElement == null)
		{
			System.err.println("Your " + SETTINGS_FILE + "appears to be corrupt.");
			System.err.println("Could not find the root element of the document.");
		}
		
		//Find all the nodes in the document with tag name property
		NodeList nodes = doc.getElementsByTagName(property);
		
		//There should be only one or less
		if (nodes.getLength() > 1)
		{
			System.err.println("Your " + SETTINGS_FILE + "appears to be corrupt.");
			System.err.println("Multiple instances of property: " + property + " were found.");
			return;
		}
		
		Element propertyElement;
		
		//If it doesn't exist create it
		if (nodes.getLength() <= 0)
			propertyElement = doc.createElement(property);
		//Otherwise use the one we found
		else 
			propertyElement = (Element)nodes.item(0);
		
		propertyElement.setAttribute("value", value);
		rootElement.appendChild(propertyElement);
		
		// write the content into xml file
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		DOMSource source = new DOMSource(doc);
		StreamResult result = new StreamResult(settingsFile);
		transformer.transform(source, result);
		
		System.out.println("Successfully updated " + property + " to " + value);
	}
	
	@Override
	public void printUsage() 
	{
		System.err.print("Usage: "); 
		System.err.println("mend set [property name] [value]");
		System.err.println();
		System.err.println("Recognized properties:");
		System.err.println("\tlogDir\tThe directory where MEND will store and search for log files.");
		System.err.println("\tencDir\tThe directory where MEND will store and search for encrypted files. (Not including logs)");
		System.err.println("\tdecDir\tThe directory where MEND will store and search for decrypted files.");
	}


	@Override
	public void printDescription()
	{
		System.err.println("Configure the variables mend uses.");	
	}
}
