package co.samco.mend4.desktop.helper;

import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.core.OSDao;
import co.samco.mend4.core.Settings;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.output.PrintStreamProvider;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

public class ShredHelper {
    private final OSDao osDao;
    private final PrintStreamProvider log;
    private final Settings settings;
    private final I18N strings;

    @Inject
    public ShredHelper(I18N strings, OSDao osDao, Settings settings, PrintStreamProvider log) {
        this.osDao = osDao;
        this.log = log;
        this.settings = settings;
        this.strings = strings;
    }

    public String[] generateShredCommandArgs(String fileName, String commandString) {
        return commandString.replaceAll(strings.get("Shred.fileName"), fileName).split(" ");
    }

    public void tryShredFile(String absolutePath) throws IOException, CorruptSettingsException {
        String shredCommand = settings.getValue(Settings.Name.SHREDCOMMAND);
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

    public void shredFilesInDirectory(String dir) throws IOException, CorruptSettingsException {
        File[] directoryListing = osDao.getDirectoryListing(new File(dir));
        for (File child : directoryListing) {
            tryShredFile(osDao.getAbsolutePath(child));
        }
    }
}
