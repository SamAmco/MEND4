package commands;

import co.samco.mend4.core.AppProperties;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.desktop.commands.StatePrinter;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StatePrinterTest extends TestBase {
    private StatePrinter statePrinter;

    @Before
    public void setup() {
        super.setup();
        statePrinter = new StatePrinter(strings, log, osDao, settingsHelper, settings);
    }

    @Test
    public void tooManyArgs() {
        statePrinter.execute(Arrays.asList("a", "b"));
        verify(err).println(strings.getf("General.invalidArgNum", StatePrinter.COMMAND_NAME));
    }

    @Test
    public void tooFewArgs() {
        statePrinter.execute(Collections.emptyList());
        verify(err).println(strings.getf("General.invalidArgNum", StatePrinter.COMMAND_NAME));
    }

    @Test
    public void allFlag() {
        when(settingsHelper.getSettingValueWrapped(any(Settings.Name.class))).thenReturn("");
        ArgumentCaptor<String> outCaptor = ArgumentCaptor.forClass(String.class);
        statePrinter.execute(Arrays.asList(StatePrinter.ALL_FLAG));
        verify(out).println(outCaptor.capture());
        for (Settings.Name n : Settings.Name.values()) {
            Assert.assertTrue(outCaptor.getValue().contains(n.toString()));
        }
    }

    @Test
    public void logFlag() throws IOException, CorruptSettingsException {
        String logDir = "dir";
        when(settings.getValue(eq(Settings.Name.LOGDIR))).thenReturn(logDir);
        filePrintTest(StatePrinter.LOGS_FLAG, AppProperties.LOG_FILE_EXTENSION);
    }

    @Test
    public void encFlag() throws IOException, CorruptSettingsException {
        String encDir = "dir";
        when(settings.getValue(eq(Settings.Name.ENCDIR))).thenReturn(encDir);
        filePrintTest(StatePrinter.ENCS_FLAG, AppProperties.ENC_FILE_EXTENSION);
    }

    @Test
    public void printSetting() {
        Settings.Name name = Settings.Name.values()[0];
        String value = "hi";
        when(settingsHelper.getSettingValueWrapped(eq(name))).thenReturn(value);
        statePrinter.execute(Arrays.asList(name.toString()));
        verify(out).println(eq(value));
    }

    @Test
    public void fallback() {
        String unknown = "unknown property";
        statePrinter.execute(Arrays.asList(unknown));
        verify(err).println(eq(strings.getf("StatePrinter.settingNotFound", unknown)));
    }

    private void filePrintTest(String flag, String extension) throws FileNotFoundException {
        File[] files = new File[]{new File("f1"), new File("f2")};
        when(osDao.getDirectoryListing(any(File.class))).thenReturn(files);
        when(osDao.getFileExtension(any(File.class))).thenReturn(extension);
        when(osDao.getBaseName(any(File.class))).thenAnswer((Answer<String>)
                invocation -> ((File)invocation.getArguments()[0]).getName());
        ArgumentCaptor<String> outCaptor = ArgumentCaptor.forClass(String.class);
        statePrinter.execute(Arrays.asList(flag));
        verify(out).println(outCaptor.capture());
        for (File f : files) {
            Assert.assertTrue(outCaptor.getValue().contains(f.getName()));
        }
    }
}
