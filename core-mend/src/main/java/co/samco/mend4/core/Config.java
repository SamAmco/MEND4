package co.samco.mend4.core;

import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import co.samco.mend4.core.impl.SettingsImpl;
import co.samco.mend4.core.Settings.CorruptSettingsException;
import co.samco.mend4.core.Settings.InvalidSettingNameException;
import co.samco.mend4.core.Settings.UnInitializedSettingsException;

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
    public static final byte[] PASSCHECK_SALT = new byte[]{
            (byte) 0xd7, (byte) 0x73, (byte) 0x31, (byte) 0x8a,
            (byte) 0x2e, (byte) 0xc8, (byte) 0xef, (byte) 0x99
    };

    public static final IvParameterSpec STANDARD_IV;

    static {
        byte[] iv = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        STANDARD_IV = new IvParameterSpec(iv);
    }


    public static int AES_KEY_GEN_ITERATIONS = 65536;

    private static String _PREFERRED_RSA_ALG = null;

    public static String PREFERRED_RSA_ALG() throws CorruptSettingsException, InvalidSettingNameException,
            UnInitializedSettingsException {
        if (_PREFERRED_RSA_ALG != null)
            return _PREFERRED_RSA_ALG;
        //Set the rsa algorithm
        //If the user has a set preference for the algo then use that
        //TODO only commented to compile
        String storedRsaAlgStr = "";//SettingsImpl.instance().getValue(Settings.Name.PREFERREDRSA);
        if (storedRsaAlgStr != null)
            _PREFERRED_RSA_ALG = storedRsaAlgStr;
            //otherwise use the max recommended available
        else
            _PREFERRED_RSA_ALG = "RSA/ECB/PKCS1Padding";

        return _PREFERRED_RSA_ALG;
    }

    private static String _PREFERRED_AES_ALG;

    public static String PREFERRED_AES_ALG() throws CorruptSettingsException, InvalidSettingNameException,
            UnInitializedSettingsException {
        if (_PREFERRED_AES_ALG != null)
            return _PREFERRED_AES_ALG;

        //Set the aes algorithm
        //If the user has a set preference for the algo then use that
        //TODO only commented to compile
        String storedAesAlgStr = "";//SettingsImpl.instance().getValue(Settings.Name.PREFERREDAES);
        if (storedAesAlgStr != null)
            _PREFERRED_AES_ALG = storedAesAlgStr;
            //otherwise use the max recommended available
        else
            _PREFERRED_AES_ALG = "AES/CTR/NoPadding";

        return _PREFERRED_AES_ALG;
    }

    private static int _RSA_KEY_SIZE = -1;

    public static int RSA_KEY_SIZE() throws CorruptSettingsException, InvalidSettingNameException,
            UnInitializedSettingsException, NoSuchAlgorithmException {
        if (_RSA_KEY_SIZE != -1)
            return _RSA_KEY_SIZE;

        //Set the rsa key size
        //If the user has a set preference for the size then use that
        //TODO only commented to compile
        String storedRsaLimitStr = "";//SettingsImpl.instance().getValue(Settings.Name.RSAKEYSIZE);
        if (storedRsaLimitStr != null)
            _RSA_KEY_SIZE = Integer.parseInt(storedRsaLimitStr);
            //otherwise use the max recommended available
        else
            _RSA_KEY_SIZE = Cipher.getMaxAllowedKeyLength("RSA") < 4096 ? 2048 : 4096;

        return _RSA_KEY_SIZE;
    }

    private static int _AES_KEY_SIZE = -1;

    public static int AES_KEY_SIZE() throws NoSuchAlgorithmException, CorruptSettingsException,
            InvalidSettingNameException, UnInitializedSettingsException {
        if (_AES_KEY_SIZE != -1)
            return _AES_KEY_SIZE;

        //Set the aes key size
        //If the user has a set preference for the size then use that
        //TODO only commented to compile
        String storedAesLimitStr = "";//SettingsImpl.instance().getValue(Settings.Name.AESKEYSIZE);
        if (storedAesLimitStr != null)
            _AES_KEY_SIZE = Integer.parseInt(storedAesLimitStr);
            //otherwise use the max recommended available
        else
            _AES_KEY_SIZE = Cipher.getMaxAllowedKeyLength("AES") < 256 ? 128 : 256;

        return _AES_KEY_SIZE;
    }
}


















