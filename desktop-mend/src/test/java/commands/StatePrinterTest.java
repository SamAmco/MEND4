package commands;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.Settings.InvalidSettingNameException;
import co.samco.mend4.core.Settings.CorruptSettingsException;
import co.samco.mend4.desktop.commands.StatePrinter;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.dao.OSDao;
import co.samco.mend4.desktop.helper.FileResolveHelper;
import co.samco.mend4.desktop.helper.SettingsHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import helper.FakeLazy;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StatePrinterTest {
    StatePrinter statePrinter;
    private I18N strings;
    private PrintStreamProvider log;
    private PrintStream err;
    private PrintStream out;
    private OSDao osDao;
    private FileResolveHelper fileResolveHelper;
    private Settings settings;
    private SettingsHelper settingsHelper;

    @Before
    public void setup() {
        strings = new I18N("en", "UK");
        err = mock(PrintStream.class);
        out = mock(PrintStream.class);
        log = mock(PrintStreamProvider.class);
        when(log.err()).thenReturn(err);
        when(log.out()).thenReturn(out);
        osDao = mock(OSDao.class);
        fileResolveHelper = mock(FileResolveHelper.class);
        settings = mock(Settings.class);
        settingsHelper = mock(SettingsHelper.class);
        statePrinter = new StatePrinter(strings, log, osDao, fileResolveHelper,
                new FakeLazy<>(settings), settingsHelper);
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
    public void logFlag() throws InvalidSettingNameException, CorruptSettingsException, FileNotFoundException {
        String logDir = "dir";
        when(fileResolveHelper.getLogDir()).thenReturn(logDir);
        filePrintTest(StatePrinter.LOGS_FLAG, Config.LOG_FILE_EXTENSION);
    }

    @Test
    public void encFlag() throws InvalidSettingNameException, CorruptSettingsException, FileNotFoundException {
        String encDir = "dir";
        when(fileResolveHelper.getEncDir()).thenReturn(encDir);
        filePrintTest(StatePrinter.ENCS_FLAG, Config.ENC_FILE_EXTENSION);
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
