package co.samco.mend4.core.bean;

public class EncodedKeyInfo {
    private final String privateKey;
    private final String publicKey;
    private final String cipherText;

    public EncodedKeyInfo(String privateKey, String publicKey, String cipherText) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.cipherText = cipherText;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getCipherText() {
        return cipherText;
    }
}

