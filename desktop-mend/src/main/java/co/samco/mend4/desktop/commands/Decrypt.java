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
            log.err().println("Please provide the file to decrypt.");
            log.err().println(getUsageText());
            return null;
        }
        return args;
    }

    private List<String> checkShouldBeSilent(List<String> args) {
        List<String> newArgs = new ArrayList<>(args);
        if (newArgs.contains("-s")) {
            silent = true;
            newArgs.remove("-s");
        }
        return newArgs;
    }

    private List<String> assertMendUnlocked(List<String> args) {
        File privateKeyFile = new File(Config.CONFIG_PATH + Config.PRIVATE_KEY_FILE_DEC);
        //TODO update this to use OSDao
        if (!osDao.fileExists(privateKeyFile)) {
            log.err().println("MEND is Locked. Please run mend unlock");
            return null;
        }
        return args;
    }

    private List<String> tryResolveFileAsLegacyEnc(List<String> args) {
        try {
            String filePath = args.get(0);
            if (filePathIsLegacyEncId(filePath)) {
                //TODO move these file extensions out
                filePath = getEncDir() + File.separatorChar + filePath + ".enc";
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
        } else if (osDao.getFileExtension(file).equals("mend")) {
            cryptoHelper.decryptLog(file);
        } else if (osDao.getFileExtension(file).equals("enc")) {
            cryptoHelper.decryptFile(file, silent);
        } else {
            log.err().println("Failed attempting to decrypt: " + osDao.getFileName(file));
            log.err().println("MEND does not know how to decrypt this file as it does not recognize the file " +
                    "extention. Expecting either .mend or .enc");
        }
        return null;
    }

    private List<String> tryResolveFileAsLog(List<String> args) {
        try {
            String filePath = args.get(0);
            String extension = osDao.getFileExtension(filePath);
            if (extension.equals("")) {
                filePath += ".mend";
            }

            file = new File(getLogDir() + File.separatorChar + filePath);
            return args;
        } catch (CorruptSettingsException | InvalidSettingNameException e) {
            log.err().println(e.getMessage());
            return null;
        }
    }

    private List<String> fallbackFileNotFound(List<String> args) {
        log.err().println("Could not find specified file: " + file.getAbsolutePath());
        return null;
    }

    private boolean filePathIsLegacyEncId(String filePath) {
        return filePath.matches("\\d{14}") || filePath.matches("\\d{16}") || filePath.matches("\\d{17}");
    }

    private String getLogDir() throws CorruptSettingsException, InvalidSettingNameException {
        String logDir = settings.get().getValue(Config.Settings.LOGDIR);
        if (logDir == null) {
            throw new CorruptSettingsException("Defaulted to searching "
                    + Config.SETTINGS_NAMES_MAP.get(Config.Settings.LOGDIR.ordinal())
                    + " but the property is not set in your Settings file.");
        }
        return logDir;
    }

    private String getEncDir() throws InvalidSettingNameException, CorruptSettingsException {
        String encDir = settings.get().getValue(Config.Settings.ENCDIR);
        if (encDir == null) {
            throw new CorruptSettingsException("You need to set the "
                    + Config.SETTINGS_NAMES_MAP.get(Config.Settings.ENCDIR.ordinal())
                    + " property in your settings file before you can decrypt files from it.");
        }
        return encDir;
    }

    @Override
    public void execute(List<String> args) {
        executeBehaviourChain(behaviourChain, args);
    }

    @Override
    public String getUsageText() {
        return "Usage:\tmend dec [-s] <log_file_name>|<enc_file>";
    }

    @Override
    public String getDescriptionText() {
        return "To decrypt an encrypted log or other mend encrypted file.";
    }

    @Override
    protected List<String> getCommandAliases() {
        return Arrays.asList(COMMAND_NAME);
    }
}
