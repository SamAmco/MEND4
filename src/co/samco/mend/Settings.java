package co.samco.mend;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * A singleton for accessing the settings file
 * @author sam
 *
 */
public class Settings 
{
	private static Settings instance;
	
	//TODO Store your document here somehow
	
	private Settings() throws ParserConfigurationException
	{
		//TODO If settings file exitst parse it. Else create a new one.
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
		
		Document doc = docBuilder.newDocument();
		Element rootElement = doc.createElement("settings");
		doc.appendChild(rootElement);
	}
	
	public static Settings instance() throws ParserConfigurationException
	{
		if (instance == null)
			instance = new Settings();
		
		return instance;
	}
	
	
	public void setValue(String name, String value)
	{
		//TODO 
	}
	
}
