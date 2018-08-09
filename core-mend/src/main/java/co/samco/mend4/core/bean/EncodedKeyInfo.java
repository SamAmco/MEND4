package co.samco.mend4.core.bean;

public class EncodedKeyInfo {
    private final String privateKey;
    private final String publicKey;

    public EncodedKeyInfo(String privateKey, String publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public String getPublicKey() {
        return publicKey;
    }
}

