package co.samco.mend4.desktop.commands;

import co.samco.mend4.core.AppProperties;
import co.samco.mend4.core.impl.SettingsImpl;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.dao.OSDao;
import co.samco.mend4.desktop.helper.FileResolveHelper;
import co.samco.mend4.desktop.helper.MergeHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class Merge extends Command {
    public static final String COMMAND_NAME = "merge";
    public static final String FIRST_FLAG = "-1";
    public static final String SECOND_FLAG = "-2";

    private final I18N strings;
    private final PrintStreamProvider log;
    private final FileResolveHelper fileResolveHelper;
    private final MergeHelper mergeHelper;
    private final OSDao osDao;

    private final List<Function<List<String>, List<String>>> behaviourChain = Arrays.asList(
            a -> assertCorrectNumOfArgs(a),
            a -> tryMergeInPlace(a),
            a -> tryMergeToNew(a)
    );


    @Inject
    public Merge(I18N strings, PrintStreamProvider log, FileResolveHelper fileResolveHelper,
                 MergeHelper mergeHelper, OSDao osDao) {
        this.strings = strings;
        this.log = log;
        this.fileResolveHelper = fileResolveHelper;
        this.mergeHelper = mergeHelper;
        this.osDao = osDao;
    }

    private List<String> assertCorrectNumOfArgs(List<String> args) {
        if (args.size() != 3) {
            log.err().println(strings.getf("General.invalidArgNum", COMMAND_NAME));
            return null;
        }
        return args;
    }

    private List<String> tryMergeInPlace(List<String> args) {
        try {
            if (args.get(0).equals(FIRST_FLAG) || args.get(0).equals(SECOND_FLAG)) {
                Pair<File, File> logFiles = resolveFiles(args.get(1), args.get(2));
                mergeHelper.mergeToFirstOrSecond(logFiles, args.get(0).equals(FIRST_FLAG));
                return null;
            }
        } catch (SettingsImpl.InvalidSettingNameException | SettingsImpl.CorruptSettingsException
                | FileNotFoundException e) {
            e.printStackTrace();
        }
        return args;
    }

    private List<String> tryMergeToNew(List<String> args) {
        try {
            Pair<File, File> logFiles = resolveFiles(args.get(0), args.get(1));
            mergeHelper.mergeLogFilesToNew(logFiles, new File(args.get(2)));
            return null;
        } catch (SettingsImpl.InvalidSettingNameException | SettingsImpl.CorruptSettingsException
                | FileNotFoundException e) {
            e.printStackTrace();
        }
        return args;
    }

    private Pair<File, File> resolveFiles(String file1, String file2)
            throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException, FileNotFoundException {
        File firstLog = fileResolveHelper.resolveLogFilePath(file1);
        File secondLog = fileResolveHelper.resolveLogFilePath(file2);
        fileResolveHelper.assertFileExistsAndHasExtension(file1, AppProperties.LOG_FILE_EXTENSION, firstLog);
        fileResolveHelper.assertFileExistsAndHasExtension(file2, AppProperties.LOG_FILE_EXTENSION, secondLog);
        return new ImmutablePair<>(firstLog, secondLog);
    }


    @Override
    public void execute(List<String> args) {
        executeBehaviourChain(behaviourChain, args);
    }

    @Override
    public String getUsageText() {
        return strings.getf("Merge.usage", COMMAND_NAME, FIRST_FLAG, SECOND_FLAG);
    }

    @Override
    public String getDescriptionText() {
        return strings.get("Merge.description");
    }

    @Override
    protected List<String> getCommandAliases() {
        return Arrays.asList(COMMAND_NAME);
    }

}
