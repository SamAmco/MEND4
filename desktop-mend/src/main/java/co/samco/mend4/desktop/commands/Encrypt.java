package co.samco.mend4.desktop.commands;

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
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.inject.Inject;
import javax.xml.transform.TransformerException;

import org.apache.commons.io.FilenameUtils;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.EncryptionUtils;
import co.samco.mend4.core.EncryptionUtils.MissingPublicKeyException;
import co.samco.mend4.desktop.core.ApacheCommonsEncoder;
import co.samco.mend4.desktop.core.InputBox;
import co.samco.mend4.desktop.core.InputBoxListener;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.Settings.CorruptSettingsException;
import co.samco.mend4.core.Settings.InvalidSettingNameException;
import co.samco.mend4.core.Settings.UnInitializedSettingsException;

public class Encrypt extends Command implements InputBoxListener {
    private final String COMMAND_NAME = "enc";
    private InputBox inputBox;
    protected boolean dropHeader = false;

    @Inject
    public Encrypt() { }

    @Override
    public void execute(List<String> args) {
        if (printHelp(args))
            return;

        readOptions(args);

        if (args.size() <= 0) {
            inputBox = new InputBox(true, false, 800, 250);
            inputBox.addListener(this);
            inputBox.setVisible(true);
        } else if (args.size() > 0) {
            if (args.contains("-d")) {
                int index = args.indexOf("-d");
                if (args.size() < index + 2) {
                    System.err.println("You must provide the text you wish to encrypt after the -d option");
                    return;
                }
                encryptTextToLog(args.get(index + 1).toCharArray(), dropHeader);
                return;
            }

            if (args.size() > 2)
                System.err.println("Invalid number of arguments. See \"mend enc -h\" for more information.");

            String name = null;
            if (args.size() > 1)
                name = args.get(1);

            encryptFile(args.get(0), name);

            return;
        }
    }

    protected void readOptions(List<String> args) {
        if (args.contains("-a")) {
            dropHeader = true;
            args.remove("-a");
        }
    }

    @Override
    public String getUsageText() {
        return "Usage:\tmend enc [-a][-d <text>|<path to file> [<encrypted file name>]]";
    }

    @Override
    public String getDescriptionText() {
        return "To encrypt text to your current log, or encrypt a file and recieve an id for it.";
    }

    @Override
    protected List<String> getCommandAliases() {
        return Arrays.asList(COMMAND_NAME);
    }

    @Override
    public void OnEnter(char[] password) {
    }

    @Override
    public void OnShiftEnter(char[] text) {
        inputBox.clear();
        encryptTextToLog(text, dropHeader);
        inputBox.close();
    }

    @Override
    public void OnCtrlEnter(char[] text) {
        inputBox.clear();
        encryptTextToLog(text, dropHeader);
    }

    private void encryptFile(String filePath, String name) {
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
                String encLocation = Settings.instance().getValue(Config.Settings.ENCDIR);
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
        } catch (CorruptSettingsException | InvalidSettingNameException
                | UnInitializedSettingsException | IOException | InvalidKeyException
                | NoSuchAlgorithmException | NoSuchPaddingException | InvalidAlgorithmParameterException
                | InvalidKeySpecException | IllegalBlockSizeException | BadPaddingException e) {
            System.err.println(e.getMessage());
        } catch (MissingPublicKeyException e) {
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

    protected void encryptTextToLog(char[] text, boolean dropHeader) {
        if (text.length <= 0)
            return;

        FileOutputStream fos = null;
        try {
            String logDir = Settings.instance().getValue(Config.Settings.LOGDIR);
            String currentLogName = Settings.instance().getValue(Config.Settings.CURRENTLOG);
            if (logDir == null)
                throw new IOException("You need to set the " + Config.SETTINGS_NAMES_MAP.get(Config.Settings.LOGDIR
                        .ordinal())
                        + " property in your settings file before you can encrypt logs to it.");
            if (currentLogName == null) {
                Settings.instance().setValue(Config.Settings.CURRENTLOG, "Log");
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
                | InvalidKeySpecException | TransformerException | CorruptSettingsException
                | InvalidSettingNameException | NoSuchPaddingException | InvalidAlgorithmParameterException
                | UnInitializedSettingsException e) {
            System.err.println(e.getMessage());
        } catch (MissingPublicKeyException e) {
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

    @Override
    public void OnEscape() {
        inputBox.close();
    }

}
