package co.samco.mend4.desktop.helper;

import co.samco.mend4.core.AppProperties;
import co.samco.mend4.core.OSDao;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.crypto.CryptoProvider;
import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.core.exception.MalformedLogFileException;
import co.samco.mend4.core.util.LogUtils;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import javax.crypto.*;
import javax.inject.Inject;
import java.io.*;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CryptoHelper {

    private final I18N strings;
    private final PrintStreamProvider log;
    private final FileResolveHelper fileResolveHelper;
    private final Settings settings;
    private final CryptoProvider cryptoProvider;
    private final KeyHelper keyHelper;
    private final OSDao osDao;
    private final VersionHelper versionHelper;

    @Inject
    public CryptoHelper(I18N strings, PrintStreamProvider log, FileResolveHelper fileResolveHelper, Settings settings,
                        CryptoProvider cryptoProvider, KeyHelper keyHelper, OSDao osDao, VersionHelper versionHelper) {
        this.strings = strings;
        this.log = log;
        this.fileResolveHelper = fileResolveHelper;
        this.settings = settings;
        this.cryptoProvider = cryptoProvider;
        this.keyHelper = keyHelper;
        this.osDao = osDao;
        this.versionHelper = versionHelper;
    }

    public void encryptFile(File file, String name) throws IOException, CorruptSettingsException,
            InvalidKeySpecException, NoSuchAlgorithmException, IllegalBlockSizeException, InvalidKeyException,
            BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException {
        if (name == null) {
            name = new SimpleDateFormat(AppProperties.ENC_FILE_NAME_FORMAT).format(new Date());
        }

        String fileExtension = FilenameUtils.getExtension(file.getAbsolutePath());
        String encLocation = settings.getValue(Settings.Name.ENCDIR);
        File outputFile = new File(encLocation + File.separatorChar + name + "." + AppProperties.ENC_FILE_EXTENSION);
        fileResolveHelper.assertFileDoesNotExist(outputFile);

        try (InputStream fis = osDao.getInputStreamForFile(file);
            OutputStream fos = osDao.getOutputStreamForFile(outputFile)) {
            log.err().println(strings.getf("CryptoHelper.encryptingFile", outputFile.getAbsolutePath()));
            cryptoProvider.encryptEncStream(keyHelper.getPublicKey(), fis, fos, fileExtension);
            log.err().println(strings.getf("CryptoHelper.encryptFileComplete", name));
        }
    }

    public void encryptTextToLog(char[] text, boolean dropHeader) throws IOException, CorruptSettingsException,
            InvalidKeySpecException, NoSuchAlgorithmException, IllegalBlockSizeException, InvalidKeyException,
            BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException {
        if (text == null || text.length <= 0) {
            return;
        }

        File currentLogFile = fileResolveHelper.getCurrentLogFile();
        osDao.createNewFile(currentLogFile);
        String logText = new String(text);
        if (!dropHeader) {
            logText = LogUtils.addHeaderToLogText(logText, strings.get("Platform.header"),
                    versionHelper.getVersion(), strings.getNewLine());
        }

        try (OutputStream fos = osDao.getOutputStreamForFile(currentLogFile, true)) {
            cryptoProvider.encryptLogStream(keyHelper.getPublicKey(), logText, fos);
        }
    }

    public void decryptLog(File file) throws InvalidKeySpecException, IOException, NoSuchAlgorithmException,
            MalformedLogFileException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException,
            NoSuchPaddingException, InvalidAlgorithmParameterException {
        try (InputStream fis = osDao.getInputStreamForFile(file)){
            cryptoProvider.decryptLogStream(keyHelper.getPrivateKey(), fis, log.out());
        }
    }

    public void decryptFile(File file, boolean silent) throws IOException, CorruptSettingsException,
            InvalidKeySpecException, NoSuchAlgorithmException, MalformedLogFileException,
            InvalidAlgorithmParameterException, NoSuchPaddingException, BadPaddingException, IllegalBlockSizeException,
            InvalidKeyException {
        String decDir = settings.getValue(Settings.Name.DECDIR);
        fileResolveHelper.assertDirWritable(decDir);
        File outputFile = FileUtils.getFile(decDir, FilenameUtils.removeExtension(file.getName()));
        fileResolveHelper.assertFileDoesNotExist(outputFile);
        String fileExtension;

        log.err().println(strings.getf("CryptoHelper.decryptingFile", outputFile.getAbsolutePath()));
        try (InputStream fis = osDao.getInputStreamForFile(file);
            OutputStream fos = osDao.getOutputStreamForFile(outputFile)) {
            fileExtension = cryptoProvider.decryptEncStream(keyHelper.getPrivateKey(), fis, fos);
        }
        String newFileName = outputFile.getName() + "." + fileExtension;
        File newOutputFile = new File(outputFile.getParentFile().getAbsolutePath()
                + File.separator + newFileName);
        fileResolveHelper.assertFileDoesNotExist(newOutputFile);
        osDao.renameFile(outputFile, newFileName);
        log.err().println(strings.get("CryptoHelper.decryptComplete"));

        if (!silent) {
            osDao.desktopOpenFile(outputFile);
        }
    }

}
