package co.samco.mend4.desktop;

import org.apache.commons.codec.binary.Base64;

import co.samco.mend4.core.IBase64EncodingProvider;

public class ApacheCommonsEncoder implements IBase64EncodingProvider
{
	public ApacheCommonsEncoder(){}
	public byte[] decodeBase64(String base64String) 
	{
		return Base64.decodeBase64(base64String);
	}

	public String encodeBase64URLSafeString(byte[] data) 
	{
		return Base64.encodeBase64URLSafeString(data);
	}
}