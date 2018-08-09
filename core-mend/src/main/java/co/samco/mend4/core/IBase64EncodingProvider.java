package co.samco.mend4.core;

public interface IBase64EncodingProvider {
    byte[] decodeBase64(String base64String);

    String encodeBase64URLSafeString(byte[] data);
}
