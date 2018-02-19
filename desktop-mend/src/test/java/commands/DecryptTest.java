package commands;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.impl.SettingsImpl;
import co.samco.mend4.desktop.commands.Decrypt;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.dao.OSDao;
import co.samco.mend4.desktop.helper.CryptoHelper;
import co.samco.mend4.desktop.helper.FileResolveHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import testutils.FakeLazy;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class DecryptTest {
    private PrintStreamProvider log;
    private I18N strings;
    private PrintStream err;
    private PrintStream out;
    private Settings settings;
    private CryptoHelper cryptoHelper;
    private OSDao osDao;
    private Decrypt decrypt;
    private FileResolveHelper fileResolveHelper;

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
        fileResolveHelper = mock(FileResolveHelper.class);
        decrypt = new Decrypt(log, strings, new FakeLazy<>(settings), cryptoHelper, osDao, fileResolveHelper);
    }

    @Test
    public void decryptLog() throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        String logFileName = "logFile." + Config.LOG_FILE_EXTENSION;
        decryptLog(logFileName, false);
    }

    @Test
    public void decryptLogWithSilentFlag() throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        String logFileName = "logFile." + Config.LOG_FILE_EXTENSION;
        decryptLog(logFileName, true);
    }

    @Test
    public void decryptEnc() throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        String encFileName = "sam." + Config.ENC_FILE_EXTENSION;
        decryptEnc(encFileName, Arrays.asList(encFileName), false);
    }

    @Test
    public void decryptEncSilent() throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        String encFileName = "sam." + Config.ENC_FILE_EXTENSION;
        decryptEnc(encFileName, Arrays.asList(encFileName, Decrypt.SILENT_FLAG), true);
    }

    @Test
    public void logFileDoesntExist() throws FileNotFoundException {
        String fileName = "unkown.extension";
        String exceptionText = "hi";
        doThrow(new FileNotFoundException(exceptionText)).when(fileResolveHelper)
                .assertFileExistsAndHasExtension(anyString(), anyString(), any(File.class));
        decrypt.execute(Arrays.asList(fileName));
        ArgumentCaptor<String> errCapture = ArgumentCaptor.forClass(String.class);
        verify(err).println(errCapture.capture());
        Assert.assertEquals(exceptionText, errCapture.getValue());
    }

    @Test
    public void encFileDoesntExist() throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        String fileName = "unkown.extension";
        when(fileResolveHelper.resolveEncFilePath(fileName)).thenReturn(new File(fileName));
        when(fileResolveHelper.fileExistsAndHasExtension(anyString(), any(File.class))).thenReturn(false);
        decrypt.execute(Arrays.asList(fileName));
        verify(fileResolveHelper).resolveLogFilePath(anyString());
    }

    @Test
    public void noArgs() {
        decrypt.execute(Collections.emptyList());
        ArgumentCaptor<String> errCapture = ArgumentCaptor.forClass(String.class);
        verify(err, times(2)).println(errCapture.capture());
        Assert.assertEquals(strings.get("Decrypt.noFile"), errCapture.getAllValues().get(0));
        Assert.assertEquals(strings.getf("Decrypt.usage", Decrypt.COMMAND_NAME, Decrypt.SILENT_FLAG), errCapture.getAllValues().get(1));
    }

    private void decryptEnc(String encFileName, List<String> args, boolean silentFlag)
            throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        when(fileResolveHelper.resolveEncFilePath(encFileName)).thenReturn(new File(encFileName));
        when(fileResolveHelper.fileExistsAndHasExtension(eq(Config.ENC_FILE_EXTENSION), any(File.class))).thenReturn(true);
        decrypt.execute(args);
        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
        verify(fileResolveHelper).fileExistsAndHasExtension(eq(Config.ENC_FILE_EXTENSION), any(File.class));
        verify(cryptoHelper).decryptFile(fileCaptor.capture(), eq(silentFlag));
        Assert.assertEquals(encFileName, fileCaptor.getValue().getName());
    }

    private void decryptLog(String logFileName, boolean silentFlag)
            throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        when(fileResolveHelper.resolveLogFilePath(anyString())).thenReturn(new File(logFileName));
        List<String> args = new ArrayList<>();
        args.add(logFileName);
        if (silentFlag) {
            args.add(Decrypt.SILENT_FLAG);
        }
        decrypt.execute(args);
        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
        verify(cryptoHelper).decryptLog(fileCaptor.capture());
        Assert.assertEquals(logFileName, fileCaptor.getValue().getName());
    }
}
