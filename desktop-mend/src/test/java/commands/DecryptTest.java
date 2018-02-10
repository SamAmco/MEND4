package commands;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.impl.SettingsImpl;
import co.samco.mend4.desktop.commands.Decrypt;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.dao.OSDao;
import co.samco.mend4.desktop.helper.CryptoHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import dagger.Lazy;
import helper.FakeLazy;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DecryptTest {
    private PrintStreamProvider log;
    private I18N strings;
    private PrintStream err;
    private PrintStream out;
    private Settings settings;
    private CryptoHelper cryptoHelper;
    private OSDao osDao;
    private Decrypt decrypt;

    @Before
    public void setup() {
        strings = new I18N("en", "UK");
        err = mock(PrintStream.class);
        out = mock(PrintStream.class);
        log = mock(PrintStreamProvider.class);
        when(log.err()).thenReturn(err);
        when(log.out()).thenReturn(out);
        settings = mock(Settings.class);
        osDao = mock(OSDao.class);
        cryptoHelper = mock(CryptoHelper.class);
        decrypt = new Decrypt(log, strings, new FakeLazy<>(settings), cryptoHelper, osDao);
    }

    @Test
    public void decryptLog() {
        String logFileName = "logFile." + Config.LOG_FILE_EXTENSION;
        filesExistAtPath(Arrays.asList(logFileName));
        when(osDao.fileIsFile(any(File.class))).thenReturn(true);
        when(osDao.getFileExtension(logFileName)).thenReturn(Config.LOG_FILE_EXTENSION);
        when(osDao.getFileExtension(any(File.class))).thenReturn(Config.LOG_FILE_EXTENSION);
        decrypt.execute(Arrays.asList(logFileName));
        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
        verify(cryptoHelper).decryptLog(fileCaptor.capture());
        Assert.assertEquals(fileCaptor.getValue().getName(), logFileName);
    }

    @Test
    public void decryptLogFromLogDir() throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        decryptLogFromLogDir("logFile." + Config.LOG_FILE_EXTENSION);
    }

    @Test
    public void decryptLogFromLogDirNoExtension() throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        decryptLogFromLogDir("logFile");
    }

    private void filesExistAtPath(List<String> filePath) {
        when(osDao.fileExists(any(File.class))).thenAnswer(
                (Answer<Boolean>) invocation
                        -> filePath.contains(((File)invocation.getArguments()[0]).getPath()));
    }

    public void decryptLogFromLogDir(String logFileName) throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        String decPath = "test/";
        when(settings.getValue(Config.Settings.LOGDIR)).thenReturn(decPath);
        filesExistAtPath(Arrays.asList(decPath + logFileName));
        when(osDao.fileIsFile(any(File.class))).thenReturn(true);
        when(osDao.getFileExtension(logFileName)).thenReturn(Config.LOG_FILE_EXTENSION);
        when(osDao.getFileExtension(any(File.class))).thenReturn(Config.LOG_FILE_EXTENSION);
        decrypt.execute(Arrays.asList(logFileName));
        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);

        verify(settings).getValue(Config.Settings.LOGDIR);
        verify(cryptoHelper).decryptLog(fileCaptor.capture());
        Assert.assertEquals(fileCaptor.getValue().getName(), logFileName);
    }
}
