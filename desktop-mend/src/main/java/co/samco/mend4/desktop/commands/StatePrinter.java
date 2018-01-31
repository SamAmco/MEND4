package co.samco.mend4.desktop.commands;

import java.io.File;
import java.util.List;

import org.apache.commons.io.FilenameUtils;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.Settings.CorruptSettingsException;
import co.samco.mend4.core.Settings.InvalidSettingNameException;
import co.samco.mend4.core.Settings.UnInitializedSettingsException;

public class StatePrinter extends Command {

    @Override
    public void execute(List<String> args) {
        if (printHelp(args))
            return;

        if (args.size() < 1 || args.size() > 1) {
            System.err.println("Wrong number of arguments.");
            getUsageText();
            return;
        }

        try {
            if (args.get(0).equals("-a")) {
                for (int i = 0; i < Config.SETTINGS_NAMES_MAP.size(); i++) {
                    String key = Config.SETTINGS_NAMES_MAP.get(i);
                    String value = Settings.instance().getValue(Config.Settings.values()[i]);
                    StringBuilder sb = new StringBuilder();
                    sb.append(key);
                    sb.append("\t");
                    if (value == null)
                        sb.append("NOT SET");
                    else sb.append(value);
                    System.err.println(sb.toString());
                    System.err.println();
                }
            } else if (args.get(0).equals("-l")) {
                String logDir = Settings.instance().getValue(Config.Settings.LOGDIR);
                if (logDir == null) {
                    System.err.println("You need to set the "
                            + Config.SETTINGS_NAMES_MAP.get(Config.Settings.LOGDIR.ordinal())
                            + " property of your settings file before you can list the files in it.");
                    return;
                }

                File logDirFile = new File(logDir);
                if (!logDirFile.exists() || !logDirFile.isDirectory()) {
                    System.err.println("Your "
                            + Config.SETTINGS_NAMES_MAP.get(Config.Settings.LOGDIR.ordinal())
                            + " does not appear to be valid: "
                            + logDirFile.getAbsolutePath());
                    return;
                }

                File[] files = logDirFile.listFiles();
                for (File f : files) {
                    if (FilenameUtils.getExtension(f.getAbsolutePath()).equals("mend")) {
                        System.out.println(FilenameUtils.getBaseName(f.getAbsolutePath()));
                    }
                }
            } else if (args.get(0).equals("-e")) {
                String encDir = Settings.instance().getValue(Config.Settings.ENCDIR);
                if (encDir == null) {
                    System.err.println("You need to set the "
                            + Config.SETTINGS_NAMES_MAP.get(Config.Settings.ENCDIR.ordinal())
                            + " property of your settings file before you can list the files in it.");
                    return;
                }

                File encDirFile = new File(encDir);
                if (!encDirFile.exists() || !encDirFile.isDirectory()) {
                    System.err.println("Your "
                            + Config.SETTINGS_NAMES_MAP.get(Config.Settings.ENCDIR.ordinal())
                            + " does not appear to be valid: "
                            + encDirFile.getAbsolutePath());
                    return;
                }

                File[] files = encDirFile.listFiles();
                for (File f : files) {
                    if (FilenameUtils.getExtension(f.getAbsolutePath()).equals("enc")) {
                        System.out.println(FilenameUtils.getBaseName(f.getAbsolutePath()));
                    }
                }
            } else {
                String value = null;
                for (int i = 0; i < Config.SETTINGS_NAMES_MAP.size(); i++) {
                    if (Config.SETTINGS_NAMES_MAP.get(i).equals(args.get(0))) {
                        value = Settings.instance().getValue(Config.Settings.values()[i]);
                        break;
                    }
                }

                if (value == null)
                    System.err.println("Value not found.");
                else System.out.println(value);
            }
        } catch (CorruptSettingsException | InvalidSettingNameException | UnInitializedSettingsException e) {
            System.err.println(e.getMessage());
        }
    }

    @Override
    public String getUsageText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Usage:\tmend get [-a | -l | -e] | <property>");
        sb.append("\n");
        sb.append("\n-a\tPrint all properties from your Setting file.");
        sb.append("\n-l\tPrint the names of log files.");
        sb.append("\n-e\tPrint the names of enc files.");
        sb.append("\n");
        sb.append("\nRecognized properties:");

        for (int i = 0; i < Config.Settings.values().length; i++) {
            sb.append("\n\t");
            sb.append(Config.SETTINGS_NAMES_MAP.get(i));
            sb.append("\t\t");
            sb.append(Config.SETTINGS_DESCRIPTIONS_MAP.get(i));
        }
        return sb.toString();
    }

    @Override
    public String getDescriptionText() {
        return "Get the values of properties in your settings file.";
    }

}
