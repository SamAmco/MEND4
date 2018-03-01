package co.samco.mend4.desktop.helper;

import co.samco.mend4.core.AppProperties;
import co.samco.mend4.core.OSDao;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.crypto.CryptoProvider;
import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.core.exception.MalformedLogFileException;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import javax.crypto.*;
import javax.inject.Inject;
import java.io.*;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

//TODO more helpful exception handling and throwing, most lower level exceptions should be caught on this layer
//TODO print some sort of helpful message if mend is not unlocked
public class CryptoHelper {

    private final I18N strings;
    private final PrintStreamProvider log;
    private final FileResolveHelper fileResolveHelper;
    private final Settings settings;
    private final CryptoProvider cryptoProvider;
    private final KeyHelper keyHelper;
    private final OSDao osDao;

    @Inject
    public CryptoHelper(I18N strings, PrintStreamProvider log, FileResolveHelper fileResolveHelper, Settings settings,
                        CryptoProvider cryptoProvider, KeyHelper keyHelper, OSDao osDao) {
        this.strings = strings;
        this.log = log;
        this.fileResolveHelper = fileResolveHelper;
        this.settings = settings;
        this.cryptoProvider = cryptoProvider;
        this.keyHelper = keyHelper;
        this.osDao = osDao;
    }

    public void encryptFile(File file, String name) throws IOException, CorruptSettingsException,
            InvalidKeySpecException, NoSuchAlgorithmException, IllegalBlockSizeException, InvalidKeyException,
            BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException {
        if (name == null)
            name = new SimpleDateFormat(AppProperties.ENC_FILE_NAME_FORMAT).format(new Date());

        String fileExtension = FilenameUtils.getExtension(file.getAbsolutePath());
        String encLocation = settings.getValue(Settings.Name.ENCDIR);
        File outputFile = new File(encLocation + File.separatorChar + name + AppProperties.ENC_FILE_EXTENSION);
        fileResolveHelper.assertFileDoesNotExist(outputFile);

        try (FileInputStream fis = new FileInputStream(file);
            FileOutputStream fos = new FileOutputStream(outputFile)) {
            log.err().println("Encrypting file to: " + outputFile.getAbsolutePath());
            cryptoProvider.encryptEncStream(keyHelper.getPublicKey(), fis, fos, fileExtension);
            log.err().println("Encryption complete. Key: " + name);
        }
    }

    private String addHeaderToLogText(String logText, String platformHeader) {
        StringBuilder sb = new StringBuilder();
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat(AppProperties.LOG_DATE_FORMAT, Locale.ENGLISH);
        sb.append(sdf.format(cal.getTime()));
        sb.append(String.format(AppProperties.LOG_HEADER, AppProperties.CORE_VERSION_NUMBER, platformHeader));
        sb.append(strings.getNewLine());
        sb.append(logText);
        return sb.toString();
    }

    public void encryptTextToLog(char[] text, boolean dropHeader) throws IOException, CorruptSettingsException,
            InvalidKeySpecException, NoSuchAlgorithmException, IllegalBlockSizeException, InvalidKeyException,
            BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException {
        if (text.length <= 0)
            return;

        File currentLogFile = fileResolveHelper.getCurrentLogFile();
        currentLogFile.createNewFile();
        String logText = new String(text);
        if (!dropHeader) {
            addHeaderToLogText(logText, strings.get("TODO"));
        }

        try (FileOutputStream fos = new FileOutputStream(currentLogFile, true)) {
            cryptoProvider.encryptLogStream(keyHelper.getPublicKey(), logText, fos);
        }

        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date date = new Date();
        log.out().println("Successfully Logged entry at: " + dateFormat.format(date));
    }

    public void decryptLog(File file) throws InvalidKeySpecException, IOException, NoSuchAlgorithmException,
            MalformedLogFileException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException,
            InvalidAlgorithmParameterException {
        try (FileInputStream fis = new FileInputStream(file)){
            cryptoProvider.decryptLogStream(keyHelper.getPrivateKey(), fis, log.out());
        }
    }

    public void decryptFile(File file, boolean silent) throws IOException, CorruptSettingsException,
            InvalidKeySpecException, NoSuchAlgorithmException, MalformedLogFileException,
            InvalidAlgorithmParameterException, NoSuchPaddingException, BadPaddingException, IllegalBlockSizeException, InvalidKeyException {
        String decDir = settings.getValue(Settings.Name.DECDIR);
        fileResolveHelper.assertDirWritable(decDir);
        File outputFile = FileUtils.getFile(decDir, FilenameUtils.removeExtension(file.getName()));
        fileResolveHelper.assertFileDoesNotExist(outputFile);
        String fileExtension;

        log.err().println("Decrypting the file to: " + outputFile.getAbsolutePath());
        try (FileInputStream fis = new FileInputStream(file);
            FileOutputStream fos = new FileOutputStream(outputFile)) {
            fileExtension = cryptoProvider.decryptEncStream(keyHelper.getPrivateKey(), fis, fos);
        }
        osDao.renameFile(outputFile, outputFile.getName() + "." + fileExtension);
        log.err().println("Decryption complete.");

        if (!silent) {
            osDao.desktopOpenFile(outputFile);
        }
    }

    public KeyPair readKeyPairFromFiles(File privateKeyFile, File publicKeyFile)
            throws InvalidKeySpecException, NoSuchAlgorithmException {
        return cryptoProvider.getKeyPairFromBytes(
                osDao.readAllFileBytes(privateKeyFile.toPath()),
                osDao.readAllFileBytes(publicKeyFile.toPath()));
    }
}
