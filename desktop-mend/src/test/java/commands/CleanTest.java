package commands;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.impl.SettingsImpl;
import co.samco.mend4.desktop.commands.Clean;
import co.samco.mend4.desktop.dao.OSDao;
import co.samco.mend4.desktop.helper.ShredHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import helper.FakeLazy;
import helper.TestUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.*;
import java.util.Collections;
import java.util.stream.IntStream;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class CleanTest {
    private Clean clean;
    private Settings settings;
    private OSDao osDao;
    private PrintStreamProvider printStreamProvider;
    private PrintStream err;
    private PrintStream out;
    private ShredHelper shredHelper;

    @Before
    public void setup() {
        err = mock(PrintStream.class);
        out = mock(PrintStream.class);
        printStreamProvider = mock(PrintStreamProvider.class);
        when(printStreamProvider.err()).thenReturn(err);
        when(printStreamProvider.out()).thenReturn(out);
        settings = mock(Settings.class);
        osDao = mock(OSDao.class);
        shredHelper = new ShredHelper(osDao, new FakeLazy<>(settings), printStreamProvider);
        clean = new Clean(printStreamProvider, new FakeLazy<>(settings), shredHelper);
    }

    @Test
    public void executesCorrectCommand() throws SettingsImpl.InvalidSettingNameException,
            SettingsImpl.CorruptSettingsException, IOException, InterruptedException {
        when(settings.getValue(Config.Settings.DECDIR)).thenReturn("");
        when(settings.getValue(Config.Settings.SHREDCOMMAND)).thenReturn("shred -u <filename> and do other stuff");
        when(osDao.getDirectoryListing(any(File.class))).thenReturn(new File[] { new File("a") });
        when(osDao.getAbsolutePath(any(File.class))).thenReturn("a");
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(TestUtils.getEmptyInputStream());
        ArgumentCaptor<String[]> commandArgs = ArgumentCaptor.forClass(String[].class);
        when(osDao.executeCommand(commandArgs.capture())).thenReturn(process);
        clean.execute(Collections.emptyList());
        Assert.assertArrayEquals(commandArgs.getValue(), new String[] {"shred", "-u", "a", "and", "do", "other", "stuff"});
    }

    @Test
    public void executesCommandCorrectNumberOfTimes() throws SettingsImpl.InvalidSettingNameException,
            SettingsImpl.CorruptSettingsException, IOException, InterruptedException {
        when(settings.getValue(Config.Settings.DECDIR)).thenReturn("");
        when(settings.getValue(Config.Settings.SHREDCOMMAND)).thenReturn("shred -u <filename> and do other stuff");
        File[] files = IntStream.range(0, 26)
                .mapToObj(i -> new File(""))
                .toArray(File[]::new);
        when(osDao.getDirectoryListing(any(File.class))).thenReturn(files);
        when(osDao.getAbsolutePath(any(File.class))).thenReturn("a");
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(TestUtils.getEmptyInputStream());
        when(osDao.executeCommand(any(String[].class))).thenReturn(process);
        clean.execute(Collections.emptyList());
        verify(osDao, times(26)).executeCommand(any(String[].class));
    }

    @Test
    public void shouldPrintErrorWhenNullDecDir() throws SettingsImpl.InvalidSettingNameException,
            SettingsImpl.CorruptSettingsException, IOException, InterruptedException {
        when(settings.getValue(Config.Settings.DECDIR)).thenReturn(null);
        when(settings.getValue(Config.Settings.SHREDCOMMAND)).thenReturn("");
        when(osDao.getDirectoryListing(any(File.class))).thenReturn(new File[] {new File("")});
        ArgumentCaptor<String> errorOutput = ArgumentCaptor.forClass(String.class);
        clean.execute(Collections.emptyList());
        verify(err).println(errorOutput.capture());
        Assert.assertTrue(errorOutput.getValue().contains("You need to set the "
                + Config.SETTINGS_NAMES_MAP.get(Config.Settings.DECDIR.ordinal())));
    }

    @Test
    public void shouldPrintErrorWhenNullShredCommand() throws SettingsImpl.InvalidSettingNameException,
            SettingsImpl.CorruptSettingsException, IOException, InterruptedException {
        when(settings.getValue(Config.Settings.DECDIR)).thenReturn("");
        when(settings.getValue(Config.Settings.SHREDCOMMAND)).thenReturn(null);
        when(osDao.getDirectoryListing(any(File.class))).thenReturn(new File[] {new File("")});
        ArgumentCaptor<String> errorOutput = ArgumentCaptor.forClass(String.class);
        clean.execute(Collections.emptyList());
        verify(err).println(errorOutput.capture());
        Assert.assertTrue(errorOutput.getValue().contains("You need to set the "
                + Config.SETTINGS_NAMES_MAP.get(Config.Settings.SHREDCOMMAND.ordinal())));
    }
}
