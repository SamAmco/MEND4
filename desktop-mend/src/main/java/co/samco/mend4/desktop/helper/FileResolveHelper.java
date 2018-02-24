package co.samco.mend4.desktop.helper;

import co.samco.mend4.core.AppProperties;
import co.samco.mend4.core.CorruptSettingsException;
import co.samco.mend4.core.OSDao;
import co.samco.mend4.core.Settings;
import co.samco.mend4.desktop.core.I18N;
import dagger.Lazy;
import org.apache.commons.io.FileUtils;

import javax.inject.Inject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.interfaces.RSAPrivateKey;

public class FileResolveHelper {
    private final OSDao osDao;
    private final Lazy<Settings> settings;
    private final I18N strings;

    @Inject
    public FileResolveHelper(OSDao osDao, Lazy<Settings> settings, I18N strings) {
        this.osDao = osDao;
        this.settings = settings;
        this.strings = strings;
    }

    private boolean fileExists(File file) {
        return file != null && osDao.fileExists(file) && osDao.fileIsFile(file);
    }

    public boolean fileExistsAndHasExtension(String extension, File file) {
        return fileExists(file) && osDao.getFileExtension(file).equals(extension);
    }

    public void assertFileExists(File file) throws FileNotFoundException {
        if (!fileExists(file)) {
            throw new FileNotFoundException(strings.getf("General.unknownIdentifier", file));
        }
    }

    public void assertFileExistsAndHasExtension(String name, String extension, File file) throws FileNotFoundException {
        if (!fileExistsAndHasExtension(extension, file)) {
            throw new FileNotFoundException(strings.getf("General.unknownIdentifier", name));
        }
    }

    public File resolveFile(String identifier) throws FileNotFoundException {
        File file = new File(identifier);
        assertFileExists(file);
        return file;
    }

    public String getSettingsPath() {
        return osDao.getUserHome() + File.pathSeparator
                + AppProperties.CONFIG_DIR_NAME + File.pathSeparator
                + AppProperties.SETTINGS_FILE_NAME;
    }

    public String getPublicKeyPath() {
        return osDao.getUserHome() + File.pathSeparator
                + AppProperties.CONFIG_DIR_NAME + File.pathSeparator
                + AppProperties.PUBLIC_KEY_FILE_NAME;
    }

    public String getPrivateKeyPath() {
        return osDao.getUserHome() + File.pathSeparator
                + AppProperties.CONFIG_DIR_NAME + File.pathSeparator
                + AppProperties.PRIVATE_KEY_FILE_NAME;
    }

    public RSAPrivateKey getPrivateKey() {
        //TODO implement this cached
        //File privateKeyFile = new File(Config.CONFIG_DIR_NAME + Config.PRIVATE_KEY_FILE_NAME);
        return null;
    }

    private boolean filePathIsEncId(String filePath) {
        return filePath.matches("\\d{14}") || filePath.matches("\\d{16}") || filePath.matches("\\d{17}");
    }

    public File resolveLogFilePath(String filePath) throws CorruptSettingsException, IOException {
        if (osDao.getFileExtension(filePath).equals("")) {
            return FileUtils.getFile(settings.get().getValue(Settings.Name.LOGDIR),
                    filePath + "." + AppProperties.LOG_FILE_EXTENSION);
        } else if (osDao.fileExists(new File(filePath))) {
            return new File(filePath);
        } else {
            return FileUtils.getFile(settings.get().getValue(Settings.Name.LOGDIR), filePath);
        }
    }

    public File resolveEncFilePath(String fileIdentifier) throws CorruptSettingsException, IOException {
        if (filePathIsEncId(fileIdentifier)) {
            return FileUtils.getFile(settings.get().getValue(Settings.Name.ENCDIR),
                    fileIdentifier + "." + AppProperties.ENC_FILE_EXTENSION);
        } else {
            return new File(fileIdentifier);
        }
    }

    public File getTempFile(String dir) {
        String tempName = "tmp";
        int tempSuffix = 0;
        File currentOutFile = new File(dir + File.separatorChar + tempName + tempSuffix + AppProperties.LOG_FILE_EXTENSION);
        while (osDao.fileExists(currentOutFile)) {
            tempSuffix++;
            currentOutFile = new File(dir + tempName + tempSuffix + AppProperties.LOG_FILE_EXTENSION);
        }
        return currentOutFile;
    }

}
