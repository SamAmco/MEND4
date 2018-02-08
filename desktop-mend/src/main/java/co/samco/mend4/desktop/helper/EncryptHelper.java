package co.samco.mend4.desktop.helper;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.EncryptionUtils;
import co.samco.mend4.core.impl.SettingsImpl;
import co.samco.mend4.desktop.core.ApacheCommonsEncoder;
import org.apache.commons.io.FilenameUtils;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.inject.Inject;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class EncryptHelper {

    @Inject
    public EncryptHelper() {}

    public void encryptFile(String filePath, String name) {
        //If there isn't a file name given, then let's generate a name for the file using a timestamp (16 digits).
        if (name == null)
            name = new SimpleDateFormat("yyyyMMddHHmmssSS").format(new Date());

        File fileToEncrypt = new File(filePath);
        if (!fileToEncrypt.exists()) {
            System.err.println("Could not find file.");
            return;
        }

        FileInputStream fis = null;
        try {
            FileOutputStream fos = null;
            try {
                //Make sure you're able to set up a file input stream
                fis = new FileInputStream(fileToEncrypt);
                //Check that you're able to set up an output stream
                String encLocation = SettingsImpl.instance().getValue(Config.Settings.ENCDIR);
                if (encLocation == null) {
                    throw new IOException("You need to set the " + Config.SETTINGS_NAMES_MAP.get(Config.Settings
                            .ENCDIR.ordinal())
                            + " property in your settings file before you can encrypt files to it.");
                }
                File outputFile = new File(encLocation + File.separatorChar + name + ".enc");
                if (outputFile.exists()) {
                    System.err.println("The output file already exists: " + outputFile.getAbsolutePath());
                }
                fos = new FileOutputStream(outputFile);

                String fileExtension = FilenameUtils.getExtension(fileToEncrypt.getAbsolutePath());

                //now we can append all the encrypted file bytes
                System.err.println("Encrypting file to: " + outputFile.getAbsolutePath());
                EncryptionUtils.encryptFileToStream(new ApacheCommonsEncoder(), fis, fos, fileExtension);
                System.err.println("Encryption complete. Key: " + name);

            } finally {
                if (fos != null)
                    fos.close();
            }
        } catch (SettingsImpl.CorruptSettingsException | SettingsImpl.InvalidSettingNameException
                | SettingsImpl.UnInitializedSettingsException | IOException | InvalidKeyException
                | NoSuchAlgorithmException | NoSuchPaddingException | InvalidAlgorithmParameterException
                | InvalidKeySpecException | IllegalBlockSizeException | BadPaddingException e) {
            System.err.println(e.getMessage());
        } catch (EncryptionUtils.MissingPublicKeyException e) {
            System.err.println("Failed to find your public key. Please ensure you have run \"mend setup\" "
                    + "and that your Settings are not corrupt or in-accessable to mend");
        } finally {
            try {
                if (fis != null)
                    fis.close();
            } catch (IOException e1) {
                System.err.println("MEND encountered an error closing file input stream.");
                e1.printStackTrace();
            }
        }
    }

    public void encryptTextToLog(char[] text, boolean dropHeader) {
        if (text.length <= 0)
            return;

        FileOutputStream fos = null;
        try {
            String logDir = SettingsImpl.instance().getValue(Config.Settings.LOGDIR);
            String currentLogName = SettingsImpl.instance().getValue(Config.Settings.CURRENTLOG);
            if (logDir == null)
                throw new IOException("You need to set the " + Config.SETTINGS_NAMES_MAP.get(Config.Settings.LOGDIR
                        .ordinal())
                        + " property in your settings file before you can encrypt logs to it.");
            if (currentLogName == null) {
                SettingsImpl.instance().setValue(Config.Settings.CURRENTLOG, "Log");
                currentLogName = "Log";
            }

            if (currentLogName.length() < 1)
                currentLogName = "Log";
            if (currentLogName.length() < 6 || !currentLogName.substring(currentLogName.length() - 5, currentLogName
                    .length()).equals(".mend"))
                currentLogName += ".mend";

            File currentLogFile = new File(logDir + File.separatorChar + currentLogName);
            currentLogFile.createNewFile();
            fos = new FileOutputStream(currentLogFile, true);

            EncryptionUtils.encryptLogToStream(new ApacheCommonsEncoder(), fos, text, dropHeader);

            DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            Date date = new Date();
            System.out.println("Successfully Logged entry at: " + dateFormat.format(date));
        } catch (NoSuchAlgorithmException | IOException | InvalidKeyException
                | IllegalBlockSizeException | BadPaddingException
                | InvalidKeySpecException | TransformerException | SettingsImpl.CorruptSettingsException
                | SettingsImpl.InvalidSettingNameException | NoSuchPaddingException | InvalidAlgorithmParameterException
                | SettingsImpl.UnInitializedSettingsException e) {
            System.err.println(e.getMessage());
        } catch (EncryptionUtils.MissingPublicKeyException e) {
            System.err.println("Failed to find your public key. Please ensure you have run \"mend setup\" "
                    + "and that your Settings are not corrupt or in-accessable to mend");
        } finally {
            try {
                if (fos != null)
                    fos.close();
            } catch (IOException e1) {
                System.err.println("MEND encountered a fatal error.");
                e1.printStackTrace();
            }
        }
    }
}
