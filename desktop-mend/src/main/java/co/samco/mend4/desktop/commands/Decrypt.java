package co.samco.mend4.desktop.commands;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import javax.inject.Inject;

import co.samco.mend4.core.Settings;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.dao.OSDao;
import co.samco.mend4.desktop.helper.CryptoHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import dagger.Lazy;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.impl.SettingsImpl.CorruptSettingsException;
import co.samco.mend4.core.impl.SettingsImpl.InvalidSettingNameException;
import org.apache.commons.io.FileUtils;

public class Decrypt extends Command {
    public static final String COMMAND_NAME = "dec";
    public static final String SILENT_FLAG = "dec";

    private final CryptoHelper cryptoHelper;
    private final PrintStreamProvider log;
    private final OSDao osDao;
    private final I18N strings;
    private final Lazy<Settings> settings;

    private boolean silent;
    private String fileIdentifier;

    private final List<Function<List<String>, List<String>>> behaviourChain = Arrays.asList(
            a -> getFileIdentifier(a),
            a -> checkShouldBeSilent(a),
            a -> tryResolveFileAsEncId(a),
            a -> tryResolveFileAsLog(a),
            a -> fallbackFileNotFound(a)
    );

    @Inject
    public Decrypt(PrintStreamProvider log, I18N strings, Lazy<Settings> settings,
                   CryptoHelper cryptoHelper, OSDao osDao) {
        this.cryptoHelper = cryptoHelper;
        this.log = log;
        this.osDao = osDao;
        this.strings = strings;
        this.settings = settings;
    }

    private List<String> getFileIdentifier(List<String> args) {
        if (args.size() < 1) {
            log.err().println(strings.get("Decrypt.noFile"));
            log.err().println(getUsageText());
            return null;
        }
        fileIdentifier = args.get(0);
        return args;
    }

    private List<String> checkShouldBeSilent(List<String> args) {
        List<String> newArgs = new ArrayList<>(args);
        if (newArgs.contains(SILENT_FLAG)) {
            silent = true;
            newArgs.remove(SILENT_FLAG);
        }
        return newArgs;
    }

    private List<String> tryResolveFileAsEncId(List<String> args) {
        try {
            File file = resolveEncFilePath(fileIdentifier);
            if (existsAndHasExtension(file, Config.ENC_FILE_EXTENSION)) {
                cryptoHelper.decryptFile(file, silent);
                return null;
            } else return args;
        } catch (InvalidSettingNameException | CorruptSettingsException e) {
            log.err().println(e.getMessage());
        }
        return null;
    }

    private File resolveEncFilePath(String filePath) throws InvalidSettingNameException, CorruptSettingsException {
        if (filePathIsEncId(filePath)) {
            return FileUtils.getFile(getEncDir(), filePath + "." + Config.ENC_FILE_EXTENSION);
        } else {
            return new File(filePath);
        }
    }

    private File resolveLogFilePath(String filePath) throws InvalidSettingNameException, CorruptSettingsException {
        if (osDao.getFileExtension(filePath).equals("")) {
            return FileUtils.getFile(getLogDir(), filePath + "." + Config.LOG_FILE_EXTENSION);
        } else if (osDao.fileExists(new File(filePath))) {
            return new File(filePath);
        } else {
            return FileUtils.getFile(getLogDir(), filePath);
        }
    }

    private boolean existsAndHasExtension(File file, String extension) {
        return osDao.fileExists(file)
                    && osDao.fileIsFile(file)
                    && osDao.getFileExtension(file).equals(extension);
    }

    private List<String> tryResolveFileAsLog(List<String> args) {
        try {
            File file = resolveLogFilePath(fileIdentifier);
            if (existsAndHasExtension(file, Config.LOG_FILE_EXTENSION)) {
                cryptoHelper.decryptLog(file);
                return null;
            } else return args;
        } catch (CorruptSettingsException | InvalidSettingNameException e) {
            log.err().println(e.getMessage());
            return null;
        }
    }

    private List<String> fallbackFileNotFound(List<String> args) {
        log.err().println(strings.getf("Decrypt.unknownIdentifier", fileIdentifier));
        return null;
    }

    private boolean filePathIsEncId(String filePath) {
        return filePath.matches("\\d{14}") || filePath.matches("\\d{16}") || filePath.matches("\\d{17}");
    }

    private String getLogDir() throws CorruptSettingsException, InvalidSettingNameException {
        String logDir = settings.get().getValue(Config.Settings.LOGDIR);
        if (logDir == null) {
            throw new CorruptSettingsException(strings.getf("Decrypt.noLogDir",
                    Config.SETTINGS_NAMES_MAP.get(Config.Settings.LOGDIR.ordinal())));
        }
        return logDir;
    }

    private String getEncDir() throws InvalidSettingNameException, CorruptSettingsException {
        String encDir = settings.get().getValue(Config.Settings.ENCDIR);
        if (encDir == null) {
            throw new CorruptSettingsException(strings.getf("Decrypt.noEncDir",
                    Config.SETTINGS_NAMES_MAP.get(Config.Settings.ENCDIR.ordinal())));
        }
        return encDir;
    }

    @Override
    public void execute(List<String> args) {
        executeBehaviourChain(behaviourChain, args);
    }

    @Override
    public String getUsageText() {
        return strings.getf("Decrypt.usage", COMMAND_NAME, SILENT_FLAG);
    }

    @Override
    public String getDescriptionText() {
        return strings.get("Decrypt.description");
    }

    @Override
    protected List<String> getCommandAliases() {
        return Arrays.asList(COMMAND_NAME);
    }
}
