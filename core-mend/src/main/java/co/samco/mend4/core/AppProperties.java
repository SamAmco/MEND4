package co.samco.mend4.core;

import javax.crypto.spec.IvParameterSpec;

public class AppProperties {
    public static final String CORE_VERSION_NUMBER = "4.0.8";
    public static final String CONFIG_DIR_NAME = ".MEND4";
    public static final String SETTINGS_FILE_NAME = "Settings.xml";
    public static final String PRIVATE_KEY_FILE_NAME = "prKey";
    public static final String PUBLIC_KEY_FILE_NAME = "pubKey";
    public static final String LOG_FILE_EXTENSION = "mend";
    public static final String ENC_FILE_EXTENSION = "enc";
    public static final String PASSCHECK_TEXT = "How much wood could a wood chuck chuck if a wood chuck could chuck wood?";
    public static final String PREFERRED_AES_ALG = "AES/CTR/NoPadding";
    public static final String PREFERRED_RSA_ALG = "RSA/ECB/PKCS1Padding";
    public static final int PREFERRED_RSA_KEY_SIZE = 4096;
    public static final int PREFERRED_AES_KEY_SIZE = 256;
    public static final int AES_KEY_GEN_ITERATIONS = 65536;
    public static final byte[] PASSCHECK_SALT = new byte[] {
            (byte) 0xd7, (byte) 0x73, (byte) 0x31, (byte) 0x8a,
            (byte) 0x2e, (byte) 0xc8, (byte) 0xef, (byte) 0x99
    };
    public static final IvParameterSpec STANDARD_IV =
            new IvParameterSpec(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
}

