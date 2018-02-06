package commands;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.impl.SettingsImpl;
import co.samco.mend4.desktop.commands.Clean;
import co.samco.mend4.desktop.dao.OSDao;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import helper.FakeLazy;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.stream.IntStream;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class CleanTest {
    private Clean clean;
    private Settings settings;
    private OSDao OSDao;
    private PrintStreamProvider printStreamProvider;
    private PrintStream err;
    private PrintStream out;

    @Before
    public void initializeClean() {
        err = mock(PrintStream.class);
        out = mock(PrintStream.class);
        printStreamProvider = mock(PrintStreamProvider.class);
        when(printStreamProvider.err()).thenReturn(err);
        when(printStreamProvider.out()).thenReturn(out);
        settings = mock(Settings.class);
        OSDao = mock(OSDao.class);
        clean = new Clean(printStreamProvider, new FakeLazy<>(settings), OSDao);
    }

    @Test
    public void executesCorrectCommand() throws SettingsImpl.InvalidSettingNameException,
            SettingsImpl.CorruptSettingsException, IOException, InterruptedException {
        when(settings.getValue(Config.Settings.DECDIR)).thenReturn("");
        when(settings.getValue(Config.Settings.SHREDCOMMAND)).thenReturn("shred -u <filename> and do other stuff");
        when(OSDao.getDirectoryListing(any(File.class))).thenReturn(new File[] { new File("a") });
        when(OSDao.getAbsolutePath(any(File.class))).thenReturn("a");
        Process process = mock(Process.class);
        when(process.waitFor()).thenReturn(0);
        ArgumentCaptor<String[]> commandArgs = ArgumentCaptor.forClass(String[].class);
        when(OSDao.executeCommand(commandArgs.capture())).thenReturn(process);
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
        when(OSDao.getDirectoryListing(any(File.class))).thenReturn(files);
        when(OSDao.getAbsolutePath(any(File.class))).thenReturn("a");
        Process process = mock(Process.class);
        when(process.waitFor()).thenReturn(0);
        when(OSDao.executeCommand(any(String[].class))).thenReturn(process);
        clean.execute(Collections.emptyList());
        verify(OSDao, times(26)).executeCommand(any(String[].class));
    }

    @Test
    public void shouldPrintErrorWhenNullDecDir() throws SettingsImpl.InvalidSettingNameException,
            SettingsImpl.CorruptSettingsException, IOException, InterruptedException {
        when(settings.getValue(Config.Settings.DECDIR)).thenReturn(null);
        when(settings.getValue(Config.Settings.SHREDCOMMAND)).thenReturn("");
        when(OSDao.getDirectoryListing(any(File.class))).thenReturn(new File[] {new File("")});
        ArgumentCaptor<String> errorOutput = ArgumentCaptor.forClass(String.class);
        clean.execute(Collections.emptyList());
        verify(err).println(errorOutput.capture());
        Assert.assertTrue(errorOutput.getValue().contains("You need to set the " + Config.SETTINGS_NAMES_MAP.get(Config.Settings.DECDIR.ordinal())));
    }

    @Test
    public void shouldPrintErrorWhenNullShredCommand() throws SettingsImpl.InvalidSettingNameException,
            SettingsImpl.CorruptSettingsException, IOException, InterruptedException {
        when(settings.getValue(Config.Settings.DECDIR)).thenReturn("");
        when(settings.getValue(Config.Settings.SHREDCOMMAND)).thenReturn(null);
        when(OSDao.getDirectoryListing(any(File.class))).thenReturn(new File[] {new File("")});
        ArgumentCaptor<String> errorOutput = ArgumentCaptor.forClass(String.class);
        clean.execute(Collections.emptyList());
        verify(err).println(errorOutput.capture());
        Assert.assertTrue(errorOutput.getValue().contains("You need to set the " + Config.SETTINGS_NAMES_MAP.get(Config.Settings.SHREDCOMMAND.ordinal())));
    }
}
