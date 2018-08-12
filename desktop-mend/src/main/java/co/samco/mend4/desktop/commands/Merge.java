package co.samco.mend4.desktop.commands;

import co.samco.mend4.core.AppProperties;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.core.OSDao;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.exception.MendLockedException;
import co.samco.mend4.desktop.exception.SettingRequiredException;
import co.samco.mend4.desktop.helper.FileResolveHelper;
import co.samco.mend4.desktop.helper.MergeHelper;
import co.samco.mend4.desktop.helper.SettingsHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
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
    private final SettingsHelper settingsHelper;
    private final MergeHelper mergeHelper;
    private final OSDao osDao;

    private final List<Function<List<String>, List<String>>> behaviourChain = Arrays.asList(
            a -> assertSettingsPresent(a),
            a -> assertCorrectNumOfArgs(a),
            a -> tryMergeInPlace(a),
            a -> tryMergeToNew(a)
    );

    @Inject
    public Merge(I18N strings, PrintStreamProvider log, FileResolveHelper fileResolveHelper,
                 SettingsHelper settingsHelper, MergeHelper mergeHelper, OSDao osDao) {
        this.strings = strings;
        this.log = log;
        this.fileResolveHelper = fileResolveHelper;
        this.settingsHelper = settingsHelper;
        this.mergeHelper = mergeHelper;
        this.osDao = osDao;
    }

    protected List<String> assertSettingsPresent(List<String> args) {
        try {
            settingsHelper.assertRequiredSettingsExist(new Settings.Name[]{
                            Settings.Name.PUBLICKEY, Settings.Name.PRIVATEKEY, Settings.Name.RSAKEYSIZE, Settings.Name.AESKEYSIZE,
                            Settings.Name.PREFERREDAES, Settings.Name.PREFERREDRSA, Settings.Name.ENCDIR, Settings.Name.LOGDIR},
                    COMMAND_NAME);
        } catch (IOException | SettingRequiredException e) {
            failWithMessage(log, e.getMessage());
            return null;
        }
        return args;
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
        } catch (IOException | CorruptSettingsException e) {
            failWithMessage(log, e.getMessage());
            return null;
        } catch (MendLockedException e) {
            failWithMessage(log, strings.getf("Merge.mendLocked", Unlock.COMMAND_NAME));
            return null;
        }
        return args;
    }

    private List<String> tryMergeToNew(List<String> args) {
        try {
            Pair<File, File> logFiles = resolveFiles(args.get(0), args.get(1));
            mergeHelper.mergeLogFilesToNew(logFiles, new File(args.get(2)));
        } catch (IOException | CorruptSettingsException e) {
            failWithMessage(log, e.getMessage());
        } catch (MendLockedException e) {
            failWithMessage(log, strings.getf("Merge.mendLocked", Unlock.COMMAND_NAME));
        }
        return null;
    }

    private Pair<File, File> resolveFiles(String file1, String file2)
            throws IOException, CorruptSettingsException {
        File firstLog = fileResolveHelper.resolveAsLogFilePath(file1);
        File secondLog = fileResolveHelper.resolveAsLogFilePath(file2);
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
