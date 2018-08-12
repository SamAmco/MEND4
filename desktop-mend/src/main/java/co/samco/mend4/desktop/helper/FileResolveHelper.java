package co.samco.mend4.desktop.helper;

import co.samco.mend4.core.AppProperties;
import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.core.OSDao;
import co.samco.mend4.core.Settings;
import co.samco.mend4.desktop.core.I18N;
import dagger.Lazy;
import org.apache.commons.io.FileUtils;

import javax.inject.Inject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

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

    public boolean fileExists(File file) {
        return file != null && osDao.fileExists(file) && osDao.fileIsFile(file);
    }

    public boolean fileExistsAndHasExtension(String extension, File file) {
        return fileExists(file) && osDao.getFileExtension(file).equals(extension);
    }

    public void assertFileExists(File file) throws FileNotFoundException {
        if (!fileExists(file)) {
            throw new FileNotFoundException(strings.getf("General.unknownIdentifier", file.getName()));
        }
    }

    public void assertFileDoesNotExist(File file) throws IllegalArgumentException {
        if (fileExists(file)) {
            throw new IllegalArgumentException(strings.getf("General.fileAlreadyExists", file.getAbsolutePath()));
        }
    }

    public void assertFileExistsAndHasExtension(String name, String extension, File file) throws FileNotFoundException {
        if (!fileExistsAndHasExtension(extension, file)) {
            throw new FileNotFoundException(strings.getf("General.unknownIdentifier", name));
        }
    }

    public void assertDirWritable(String dir) throws IllegalArgumentException {
        File file = new File(dir);
        if (!file.isDirectory() || !file.exists() || !file.canWrite()) {
            throw new IllegalArgumentException(strings.getf("General.couldNotFindOrWriteDir", file.getAbsolutePath()));
        }
    }

    public File resolveFile(String identifier) throws FileNotFoundException {
        File file = new File(identifier);
        assertFileExists(file);
        return file;
    }

    public String getMendDirPath() {
        return osDao.getUserHome() + File.separator
                + AppProperties.CONFIG_DIR_NAME;
    }

    public String getSettingsFilePath() {
        return getMendDirPath() + File.separator
                + AppProperties.SETTINGS_FILE_NAME;
    }

    public String getPublicKeyPath() {
        return getMendDirPath() + File.separator
                + AppProperties.PUBLIC_KEY_FILE_NAME;
    }

    public String getPrivateKeyPath() {
        return getMendDirPath() + File.separator
                + AppProperties.PRIVATE_KEY_FILE_NAME;
    }

    private String ensureLogNameHasFileExtension(String logName) {
        if (!osDao.getFileExtension(logName).equals(AppProperties.LOG_FILE_EXTENSION)) {
            logName += "." + AppProperties.LOG_FILE_EXTENSION;
        }
        return logName;
    }

    public File getCurrentLogFile() throws IOException, CorruptSettingsException {
        String currentLog = ensureLogNameHasFileExtension(
                settings.get().getValue(Settings.Name.CURRENTLOG));
        return FileUtils.getFile(settings.get().getValue(Settings.Name.LOGDIR), currentLog);
    }

    private boolean filePathIsEncId(String filePath) {
        return filePath.matches("\\d{14}") || filePath.matches("\\d{16}") || filePath.matches("\\d{17}");
    }

    public File resolveAsLogFilePath(String filePath) throws CorruptSettingsException, IOException {
        if (osDao.getFileExtension(filePath).equals("")) {
            return FileUtils.getFile(settings.get().getValue(Settings.Name.LOGDIR),
                    filePath + "." + AppProperties.LOG_FILE_EXTENSION);
        } else if (osDao.fileExists(new File(filePath))) {
            return new File(filePath);
        } else {
            filePath = ensureLogNameHasFileExtension(filePath);
            return FileUtils.getFile(settings.get().getValue(Settings.Name.LOGDIR), filePath);
        }
    }

    public File resolveAsEncFilePath(String fileIdentifier) throws CorruptSettingsException, IOException {
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
        File currentOutFile;
        do {
            currentOutFile = new File(dir + File.separator + tempName + tempSuffix + "." + AppProperties.LOG_FILE_EXTENSION);
            tempSuffix++;
        }
        while (osDao.fileExists(currentOutFile));
        return currentOutFile;
    }

}




























