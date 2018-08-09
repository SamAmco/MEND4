package co.samco.mend4.core.bean;

public class EncodedKeyInfo {
    private final String privateKey;
    private final String publicKey;
    private final int keySize;

    public EncodedKeyInfo(String privateKey, String publicKey, int keySize) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.keySize = keySize;
    }

    public int getKeySize() {
        return keySize;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public String getPublicKey() {
        return publicKey;
    }
}

