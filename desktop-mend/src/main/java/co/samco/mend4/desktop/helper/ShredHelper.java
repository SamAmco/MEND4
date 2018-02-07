package co.samco.mend4.desktop.helper;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.impl.SettingsImpl;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.dao.OSDao;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import dagger.Lazy;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

public class ShredHelper {
    private final OSDao osDao;
    private final PrintStreamProvider log;
    private final Lazy<Settings> settings;
    private final I18N strings;

    @Inject
    public ShredHelper(I18N strings, OSDao osDao, Lazy<Settings> settings, PrintStreamProvider log) {
        this.osDao = osDao;
        this.log = log;
        this.settings = settings;
        this.strings = strings;
    }

    private String mapFilename(String s, String fileName) {
        return s.equals(strings.get("Shred.fileName")) ? fileName : s;
    }

    public String[] generateShredCommandArgs(String fileName, String commandString) {
        return Arrays.stream(commandString.split(" "))
                .map(s -> mapFilename(s, fileName))
                .toArray(String[]::new);
    }

    private String getShredCommand() throws SettingsImpl.CorruptSettingsException, SettingsImpl.InvalidSettingNameException {
        String shredCommand = settings.get().getValue(Config.Settings.SHREDCOMMAND);
        if (shredCommand == null) {
            throw new SettingsImpl.CorruptSettingsException(strings.getf("Shred.noShredCommand",
                    Config.SETTINGS_NAMES_MAP.get(Config.Settings.SHREDCOMMAND.ordinal())));
        }
        return shredCommand;
    }

    public void tryShredFile(String absolutePath) throws IOException, SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        String shredCommand = getShredCommand();
        String[] shredCommandArgs = generateShredCommandArgs(absolutePath, shredCommand);
        log.err().println(strings.getf("Shred.cleaning", absolutePath));
        Process tr = osDao.executeCommand(shredCommandArgs);
        BufferedReader rd = new BufferedReader(new InputStreamReader(tr.getInputStream()));
        String s = rd.readLine();
        while (s != null) {
            log.out().println(s);
            s = rd.readLine();
        }
    }

    public void shredFilesInDirectory(String dir) throws IOException, SettingsImpl.InvalidSettingNameException,
            SettingsImpl.CorruptSettingsException {
        File[] directoryListing = osDao.getDirectoryListing(new File(dir));
        for (File child : directoryListing) {
            tryShredFile(osDao.getAbsolutePath(child));
        }
    }
}
