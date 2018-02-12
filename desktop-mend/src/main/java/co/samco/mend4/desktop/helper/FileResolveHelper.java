package co.samco.mend4.desktop.helper;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.impl.SettingsImpl;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.dao.OSDao;
import dagger.Lazy;
import org.apache.commons.io.FileUtils;

import javax.inject.Inject;
import java.io.File;
import java.io.FileNotFoundException;
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

    public String getLogDir() throws SettingsImpl.CorruptSettingsException, SettingsImpl.InvalidSettingNameException {
        String logDir = settings.get().getValue(Config.Settings.LOGDIR);
        if (logDir == null) {
            throw new SettingsImpl.CorruptSettingsException(strings.getf("FileResolve.noLogDir",
                    Config.SETTINGS_NAMES_MAP.get(Config.Settings.LOGDIR.ordinal())));
        }
        return logDir;
    }

    public boolean fileExistsAndHasExtension(String extension, File file) {
        return file != null && osDao.fileExists(file) && osDao.fileIsFile(file) && osDao.getFileExtension(file).equals(extension);
    }

    public void assertFileExistsAndHasExtension(String name, String extension, File file) throws FileNotFoundException {
        if (!fileExistsAndHasExtension(extension, file)) {
            throw new FileNotFoundException(strings.getf("General.unknownIdentifier", file));
        }
    }

    public String getEncDir() throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        String encDir = settings.get().getValue(Config.Settings.ENCDIR);
        if (encDir == null) {
            throw new SettingsImpl.CorruptSettingsException(strings.getf("FileResolve.noEncDir",
                    Config.SETTINGS_NAMES_MAP.get(Config.Settings.ENCDIR.ordinal())));
        }
        return encDir;
    }

    public RSAPrivateKey getPrivateKey() {
        //TODO implement this cached
        //File privateKeyFile = new File(Config.CONFIG_PATH + Config.PRIVATE_KEY_FILE_DEC);
        return null;
    }

    private boolean filePathIsEncId(String filePath) {
        return filePath.matches("\\d{14}") || filePath.matches("\\d{16}") || filePath.matches("\\d{17}");
    }

    public File resolveLogFilePath(String filePath) throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        if (osDao.getFileExtension(filePath).equals("")) {
            return FileUtils.getFile(getLogDir(), filePath + "." + Config.LOG_FILE_EXTENSION);
        } else if (osDao.fileExists(new File(filePath))) {
            return new File(filePath);
        } else {
            return FileUtils.getFile(getLogDir(), filePath);
        }
    }

    public File resolveEncFilePath(String filePath) throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        if (filePathIsEncId(filePath)) {
            return FileUtils.getFile(getEncDir(), filePath + "." + Config.ENC_FILE_EXTENSION);
        } else {
            return new File(filePath);
        }
    }

    public File getTempFile(String dir) {
        String tempName = "tmp";
        int tempSuffix = 0;
        File currentOutFile = new File(dir + File.separatorChar + tempName + tempSuffix + Config.LOG_FILE_EXTENSION);
        while (osDao.fileExists(currentOutFile)) {
            tempSuffix++;
            currentOutFile = new File(dir + tempName + tempSuffix + Config.LOG_FILE_EXTENSION);
        }
        return currentOutFile;
    }

}
