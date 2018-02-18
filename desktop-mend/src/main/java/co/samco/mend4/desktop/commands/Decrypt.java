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
import co.samco.mend4.desktop.helper.FileResolveHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import dagger.Lazy;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.Settings.CorruptSettingsException;
import co.samco.mend4.core.Settings.InvalidSettingNameException;

public class Decrypt extends Command {
    public static final String COMMAND_NAME = "dec";
    public static final String SILENT_FLAG = "dec";

    private final CryptoHelper cryptoHelper;
    private final PrintStreamProvider log;
    private final OSDao osDao;
    private final I18N strings;
    private final Lazy<Settings> settings;
    private final FileResolveHelper fileResolveHelper;

    private boolean silent;
    private String fileIdentifier;

    private final List<Function<List<String>, List<String>>> behaviourChain = Arrays.asList(
            a -> getFileIdentifier(a),
            a -> checkShouldBeSilent(a),
            a -> tryResolveFileAsEncId(a),
            a -> tryResolveFileAsLog()
    );

    @Inject
    public Decrypt(PrintStreamProvider log, I18N strings, Lazy<Settings> settings,
                   CryptoHelper cryptoHelper, OSDao osDao, FileResolveHelper fileResolveHelper) {
        this.cryptoHelper = cryptoHelper;
        this.log = log;
        this.osDao = osDao;
        this.strings = strings;
        this.settings = settings;
        this.fileResolveHelper = fileResolveHelper;
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
            File file = fileResolveHelper.resolveEncFilePath(fileIdentifier);
            if (fileResolveHelper.fileExistsAndHasExtension(Config.ENC_FILE_EXTENSION, file)) {
                cryptoHelper.decryptFile(file, silent);
                return null;
            } else return args;
        } catch (InvalidSettingNameException | CorruptSettingsException e) {
            log.err().println(e.getMessage());
        }
        return null;
    }

    private List<String> tryResolveFileAsLog() {
        try {
            File file = fileResolveHelper.resolveLogFilePath(fileIdentifier);
            fileResolveHelper.assertFileExistsAndHasExtension(fileIdentifier, Config.LOG_FILE_EXTENSION, file);
            cryptoHelper.decryptLog(file);
        } catch (CorruptSettingsException | InvalidSettingNameException | FileNotFoundException e) {
            log.err().println(e.getMessage());
        }
        return null;
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
