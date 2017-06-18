package co.samco.mend4.desktop;

import java.io.File;
import java.io.IOException;

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

import co.samco.mend4.core.Config;
import co.samco.mend4.core.ISettings;
import co.samco.mend4.core.Settings.CorruptSettingsException;
import co.samco.mend4.core.Settings.InvalidSettingNameException;

/**
 * A singleton for accessing the settings file
 * @author sam
 *
 */
public class DesktopSettings implements ISettings
{
	Document doc;
	Element rootElement;
	File settingsFile;
	
	public DesktopSettings() throws ParserConfigurationException, SAXException, IOException
	{
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
		
		settingsFile = new File(Config.CONFIG_PATH + Config.SETTINGS_FILE);
		if (!settingsFile.exists())
		{
			doc = docBuilder.newDocument();
			rootElement = doc.createElement("settings");
			doc.appendChild(rootElement);
		}
		else
		{
			doc = docBuilder.parse(settingsFile);
			rootElement = doc.getDocumentElement();
			rootElement.normalize();
		}
		
	}
	
	public void setValue(Config.Settings name, String value) 
			throws TransformerException, CorruptSettingsException, InvalidSettingNameException
	{
		String strName = Config.SETTINGS_NAMES_MAP.get(name.ordinal());
		if (strName == null)
			throw new InvalidSettingNameException("Could not find the given Setting name while setting value");
		if (rootElement == null)
			throw new CorruptSettingsException("Could not find the root element of the document.");
		
		//Find all the nodes in the document with tag name name
		NodeList nodes = doc.getElementsByTagName(strName);
		
		//There should be only one or less
		if (nodes.getLength() > 1)
		{
			throw new CorruptSettingsException("Multiple instances of property: " + strName + " were found.");
		}
		
		Element propertyElement;
		
		//If it doesn't exist create it
		if (nodes.getLength() <= 0)
			propertyElement = doc.createElement(strName);
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
	}
	
	public String getValue(Config.Settings name) throws CorruptSettingsException, InvalidSettingNameException
	{
		String strName = Config.SETTINGS_NAMES_MAP.get(name.ordinal());
		if (strName == null)
			throw new InvalidSettingNameException("Could not find the given Setting name while setting value");
		
		//Check that the user has the property we're looking for
		NodeList nList = doc.getElementsByTagName(strName);
		
		if (nList.getLength() <= 0)
		{
			return null;
		}
		
		if(nList.getLength() > 1)
		{
			throw new CorruptSettingsException("You have more than one element in your settings file with name " + strName);
		}
		
		Node node = nList.item(0);
		if (node.getNodeType() != Node.ELEMENT_NODE)
		{
			throw new CorruptSettingsException("Node with name " + strName + " is an invalid element node");
		}
		
		Element el = (Element)node;
		String r = el.getAttribute("value");
		
		if (r.equals(""))
			return null;
		else return r;
	}
	
	
	
}
























