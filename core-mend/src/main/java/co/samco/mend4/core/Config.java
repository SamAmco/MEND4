package co.samco.mend4.core;

import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import co.samco.mend4.core.impl.SettingsImpl;
import co.samco.mend4.core.Settings.CorruptSettingsException;
import co.samco.mend4.core.Settings.InvalidSettingNameException;
import co.samco.mend4.core.Settings.UnInitializedSettingsException;

//TODO refactor this somehow
public class Config {
    public static final String CORE_VERSION_NUMBER = "4.0.8";
    public static final String CONFIG_PATH = "";//System.getProperty("user.home") + "/.MEND4/";
    public static final String SETTINGS_FILE = "Settings.xml";
    public static final String PRIVATE_KEY_FILE_DEC = "prKey";
    public static final String PUBLIC_KEY_FILE = "pubKey";
    public static final String LOG_FILE_EXTENSION = "mend";
    public static final String ENC_FILE_EXTENSION = "enc";
    public static final String PASSCHECK_TEXT = "How much wood could a wood chuck chuck if a wood chuck could chuck " +
            "wood?";
    public static final String PREFERRED_AES_ALG = "AES/CTR/NoPadding";
    public static final String PREFERRED_RSA_ALG = "RSA/ECB/PKCS1Padding";
    public static int RSA_KEY_SIZE;
    public static int AES_KEY_SIZE;
    public static int AES_KEY_GEN_ITERATIONS = 65536;
    public static final byte[] PASSCHECK_SALT = new byte[]{
            (byte) 0xd7, (byte) 0x73, (byte) 0x31, (byte) 0x8a,
            (byte) 0x2e, (byte) 0xc8, (byte) 0xef, (byte) 0x99
    };

    public static final IvParameterSpec STANDARD_IV;

    static {
        byte[] iv = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        STANDARD_IV = new IvParameterSpec(iv);

        try {
            RSA_KEY_SIZE = Cipher.getMaxAllowedKeyLength("RSA") < 4096 ? 2048 : 4096;
            AES_KEY_SIZE = Cipher.getMaxAllowedKeyLength("AES") < 256 ? 128 : 256;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

}

