package co.samco.mend4.desktop.commands;

import co.samco.mend4.core.AppProperties;
import co.samco.mend4.core.CorruptSettingsException;
import co.samco.mend4.core.OSDao;
import co.samco.mend4.core.Settings;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.helper.SettingsHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class StatePrinter extends Command {
    public static final String COMMAND_NAME = "get";
    public static final String ALL_FLAG = "-a";
    public static final String LOGS_FLAG = "-l";
    public static final String ENCS_FLAG = "-e";

    private final I18N strings;
    private final PrintStreamProvider log;
    private final OSDao osDao;
    private final SettingsHelper settingsHelper;
    private final Settings settings;

    private String arg;

    private final List<Function<List<String>, List<String>>> behaviourChain = Arrays.asList(
            a -> getArg(a),
            a -> checkArgIsFlag(a),
            a -> checkArgIsSetting(a),
            a -> fallbackUnknown()
    );

    @Inject
    public StatePrinter(I18N strings, PrintStreamProvider log, OSDao osDao, SettingsHelper settingsHelper,
                        Settings settings) {
        this.strings = strings;
        this.log = log;
        this.osDao = osDao;
        this.settingsHelper = settingsHelper;
        this.settings = settings;
    }

    private List<String> getArg(List<String> args) {
        if (args.size() != 1) {
            log.err().println(strings.getf("General.invalidArgNum", COMMAND_NAME));
            return null;
        }
        arg = args.get(0);
        return args;
    }

    private List<String> checkArgIsFlag(List<String> args) {
        try {
            if (arg.equals(ALL_FLAG)) {
                printAll();
                return null;
            } else if (arg.equals(LOGS_FLAG)) {
                printLogs();
                return null;
            } else if (arg.equals(ENCS_FLAG)) {
                printEncs();
                return null;
            }
        } catch (IOException | CorruptSettingsException e) {
            log.err().println(e.getMessage());
        }
        return args;
    }

    private List<String> checkArgIsSetting(List<String> args) {
        Optional<String> value = Arrays.stream(Settings.Name.values())
                .filter(n -> n.toString().equals(arg))
                .map(n -> settingsHelper.getSettingValueWrapped(n))
                .findFirst();

        if (value.isPresent()) {
            log.out().println(value.get());
            return null;
        } else return args;
    }

    private List<String> fallbackUnknown() {
        log.err().println(strings.getf("StatePrinter.settingNotFound", arg));
        return null;
    }

    private void printEncs() throws IOException, CorruptSettingsException {
        File encDir = new File(settings.getValue(Settings.Name.ENCDIR));
        String encs = Arrays.stream(osDao.getDirectoryListing(encDir))
                .filter(f -> osDao.getFileExtension(f).equals(AppProperties.ENC_FILE_EXTENSION))
                .map(f -> osDao.getBaseName(f))
                .collect(Collectors.joining(strings.getNewLine()));
        log.out().println(encs);
    }

    private void printLogs() throws CorruptSettingsException, IOException {
        File logDir = new File(settings.getValue(Settings.Name.LOGDIR));
        String logs = Arrays.stream(osDao.getDirectoryListing(logDir))
                .filter(f -> osDao.getFileExtension(f).equals(AppProperties.LOG_FILE_EXTENSION))
                .map(f -> osDao.getBaseName(f))
                .collect(Collectors.joining(strings.getNewLine()));
        log.out().println(logs);
    }

    private void printAll() {
        String output = Arrays.stream(Settings.Name.values())
                .map(n -> formatSettingValue(n))
                .collect(Collectors.joining(strings.getNewLine(2)));
        log.out().println(output);
    }

    private String formatSettingValue(Settings.Name name) {
        StringBuilder sb = new StringBuilder();
        sb.append(name.toString());
        sb.append("\t");
        String value = settingsHelper.getSettingValueWrapped(name);
        sb.append(value == null ? strings.get("StatePrinter.notFound") : value);
        return sb.toString();
    }

    @Override
    public void execute(List<String> args) {
        executeBehaviourChain(behaviourChain, args);
    }

    @Override
    public String getUsageText() {
        return strings.getf("StatePrinter.usage", COMMAND_NAME, ALL_FLAG, LOGS_FLAG, ENCS_FLAG,
                ALL_FLAG, LOGS_FLAG, ENCS_FLAG, settingsHelper.getSettingDescriptions());
    }

    @Override
    public String getDescriptionText() {
        return strings.get("StatePrinter.description");
    }

    @Override
    protected List<String> getCommandAliases() {
        return Arrays.asList(COMMAND_NAME);
    }

}
