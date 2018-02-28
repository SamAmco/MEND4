package co.samco.mend4.core;

//TODO investigate whether we still need this after core is refactored
public interface IBase64EncodingProvider {
    byte[] decodeBase64(String base64String);

    String encodeBase64URLSafeString(byte[] data);
}
