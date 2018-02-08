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

public class Decrypt extends Command {
    private final String COMMAND_NAME = "dec";
    private final String SILENT_FLAG = "dec";

    private final CryptoHelper cryptoHelper;
    private final PrintStreamProvider log;
    private final OSDao osDao;
    private final I18N strings;
    private final Lazy<Settings> settings;

    private boolean silent;
    private File file;

    private final List<Function<List<String>, List<String>>> behaviourChain = Arrays.asList(
            a -> assertFileProvided(a),
            a -> checkShouldBeSilent(a),
            a -> assertMendUnlocked(a),
            a -> tryResolveFileAsLegacyEnc(a),
            a -> tryDecryptFileIfExists(a),
            a -> tryResolveFileAsLog(a),
            a -> tryDecryptFileIfExists(a),
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

    private List<String> assertFileProvided(List<String> args) {
        if (args.size() < 1) {
            log.err().println(strings.get("Decrypt.noFile"));
            log.err().println(getUsageText());
            return null;
        }
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

    private List<String> assertMendUnlocked(List<String> args) {
        File privateKeyFile = new File(Config.CONFIG_PATH + Config.PRIVATE_KEY_FILE_DEC);
        if (!osDao.fileExists(privateKeyFile)) {
            log.err().println(strings.get("Decrypt.locked"));
            return null;
        }
        return args;
    }

    private List<String> tryResolveFileAsLegacyEnc(List<String> args) {
        try {
            String filePath = args.get(0);
            if (filePathIsLegacyEncId(filePath)) {
                filePath = getEncDir() + File.separatorChar + filePath + "." + Config.ENC_FILE_EXTENSION;
            }
            file = new File(filePath);
            return args;
        } catch (InvalidSettingNameException | CorruptSettingsException e) {
            log.err().println(e.getMessage());
        }
        return null;
    }

    private List<String> tryDecryptFileIfExists(List<String> args) {
        if (!osDao.fileExists(file) || !osDao.fileIsFile(file)) {
            return args;
        } else if (osDao.getFileExtension(file).equals(Config.LOG_FILE_EXTENSION)) {
            cryptoHelper.decryptLog(file);
        } else if (osDao.getFileExtension(file).equals(Config.ENC_FILE_EXTENSION)) {
            cryptoHelper.decryptFile(file, silent);
        } else {
            log.err().println(strings.getf("Decrypt.failed", osDao.getFileName(file)));
            log.err().println(strings.getf("Decrypt.unkownType",
                    Config.LOG_FILE_EXTENSION, Config.ENC_FILE_EXTENSION));
        }
        return null;
    }

    private List<String> tryResolveFileAsLog(List<String> args) {
        try {
            String filePath = args.get(0);
            String extension = osDao.getFileExtension(filePath);
            if (extension.equals("")) {
                filePath += "." + Config.LOG_FILE_EXTENSION;
            }

            file = new File(getLogDir() + File.separatorChar + filePath);
            return args;
        } catch (CorruptSettingsException | InvalidSettingNameException e) {
            log.err().println(e.getMessage());
            return null;
        }
    }

    private List<String> fallbackFileNotFound(List<String> args) {
        log.err().println(strings.getf("Decrypt.fileNotFound", file.getAbsolutePath()));
        return null;
    }

    private boolean filePathIsLegacyEncId(String filePath) {
        return filePath.matches("\\d{14}") || filePath.matches("\\d{16}") || filePath.matches("\\d{17}");
    }

    private String getLogDir() throws CorruptSettingsException, InvalidSettingNameException {
        String logDir = settings.get().getValue(Config.Settings.LOGDIR);
        if (logDir == null) {
            throw new CorruptSettingsException(strings.getf("Decrypt.noDecDir",
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
