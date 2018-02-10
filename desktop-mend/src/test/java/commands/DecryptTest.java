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
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
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
        decryptLog(logFileName, false);
    }

    @Test
    public void decryptLogFromLogDir() throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        decryptLogFromLogDir("logFile." + Config.LOG_FILE_EXTENSION);
    }

    @Test
    public void decryptLogFromLogDirNoExtension() throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        decryptLogFromLogDir("logFile");
    }

    @Test
    public void decryptLogWithSilentFlag() {
        String logFileName = "logFile." + Config.LOG_FILE_EXTENSION;
        decryptLog(logFileName, true);
    }

    @Test
    public void decryptEnc() {
        String encFileName = "sam." + Config.ENC_FILE_EXTENSION;
        decryptEnc(encFileName, Arrays.asList(encFileName), false);
    }

    @Test
    public void decryptEncSilent() {
        String encFileName = "sam." + Config.ENC_FILE_EXTENSION;
        decryptEnc(encFileName, Arrays.asList(encFileName, Decrypt.SILENT_FLAG), true);
    }

    @Test
    public void decryptEncAsId14() throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        String encFileName = "12345678901234";
        decryptEncFromEncDir(encFileName, false, false);
    }

    @Test
    public void decryptEncAsId16() throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        String encFileName = "1234567890123456";
        decryptEncFromEncDir(encFileName, false, false);
    }

    @Test
    public void decryptEncAsId17() throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        String encFileName = "12345678901234567";
        decryptEncFromEncDir(encFileName, false, false);
    }

    @Test
    public void decryptEncAsId17Silent() throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        String encFileName = "12345678901234567";
        decryptEncFromEncDir(encFileName, true, false);
    }

    @Test
    public void decryptEncAsId14Silent() throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        String encFileName = "12345678901234";
        decryptEncFromEncDir(encFileName, true, false);
    }

    @Test
    public void decryptEncAsId16Silent() throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        String encFileName = "1234567890123456";
        decryptEncFromEncDir(encFileName, true, false);
    }

    @Test
    public void unknownFileExtension() {
        String fileName = "unkown.extension";
        filesExistAtPath(Arrays.asList(fileName));
        when(osDao.fileIsFile(any(File.class))).thenReturn(true);
        when(osDao.getFileExtension(fileName)).thenReturn("extension");
        when(osDao.getFileExtension(any(File.class))).thenReturn("extension");
        decrypt.execute(Arrays.asList(fileName));
        ArgumentCaptor<String> errCapture = ArgumentCaptor.forClass(String.class);
        verify(err).println(errCapture.capture());
        Assert.assertEquals(strings.getf("Decrypt.unknownIdentifier", fileName), errCapture.getValue());
    }

    @Test
    public void fileDoesntExist() throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        String fileName = "unkown.extension";
        when(osDao.getFileExtension(fileName)).thenReturn("extension");
        when(settings.getValue(Config.Settings.LOGDIR)).thenReturn("decPath");
        decrypt.execute(Arrays.asList(fileName));
        ArgumentCaptor<String> errCapture = ArgumentCaptor.forClass(String.class);
        verify(settings).getValue(Config.Settings.LOGDIR);
        verify(err).println(errCapture.capture());
        Assert.assertEquals(strings.getf("Decrypt.unknownIdentifier", fileName), errCapture.getValue());
    }

    @Test
    public void noArgs() {
        decrypt.execute(Collections.emptyList());
        ArgumentCaptor<String> errCapture = ArgumentCaptor.forClass(String.class);
        verify(err, times(2)).println(errCapture.capture());
        Assert.assertEquals(strings.get("Decrypt.noFile"), errCapture.getAllValues().get(0));
        Assert.assertEquals(strings.getf("Decrypt.usage", Decrypt.COMMAND_NAME, Decrypt.SILENT_FLAG), errCapture.getAllValues().get(1));
    }

    @Test
    public void noLogDir() throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        String fileName = "file." + Config.LOG_FILE_EXTENSION;
        when(osDao.getFileExtension(fileName)).thenReturn(Config.LOG_FILE_EXTENSION);
        when(osDao.getFileExtension(any(File.class))).thenReturn(Config.LOG_FILE_EXTENSION);
        when(settings.getValue(Config.Settings.LOGDIR)).thenReturn(null);
        filesExistAtPath(Arrays.asList("logPath/" + fileName));
        decrypt.execute(Arrays.asList(fileName));
        ArgumentCaptor<String> errCapture = ArgumentCaptor.forClass(String.class);
        verify(settings).getValue(Config.Settings.LOGDIR);
        verify(err).println(errCapture.capture());
        Assert.assertEquals(strings.getf("Decrypt.noLogDir",
                Config.SETTINGS_NAMES_MAP.get(Config.Settings.LOGDIR.ordinal())),
                errCapture.getValue());
    }

    @Test
    public void noEncDir() throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        String fileName = "12345678901234";
        when(osDao.getFileExtension(fileName)).thenReturn(Config.ENC_FILE_EXTENSION);
        when(osDao.getFileExtension(any(File.class))).thenReturn(Config.ENC_FILE_EXTENSION);
        when(settings.getValue(Config.Settings.ENCDIR)).thenReturn(null);
        filesExistAtPath(Arrays.asList("encPath/" + fileName));
        decrypt.execute(Arrays.asList(fileName));
        ArgumentCaptor<String> errCapture = ArgumentCaptor.forClass(String.class);
        verify(settings).getValue(Config.Settings.ENCDIR);
        verify(err).println(errCapture.capture());
        Assert.assertEquals(strings.getf("Decrypt.noEncDir",
                Config.SETTINGS_NAMES_MAP.get(Config.Settings.ENCDIR.ordinal())),
                errCapture.getValue());
    }

    private void decryptEncFromEncDir(String encFileName, boolean silentFlag, boolean hasExtension)
            throws SettingsImpl.InvalidSettingNameException, SettingsImpl.CorruptSettingsException {
        String encPath = "test/";
        when(settings.getValue(Config.Settings.ENCDIR)).thenReturn(encPath);
        String actualFileName = encFileName;
        if (!hasExtension) {
            actualFileName += "." + Config.ENC_FILE_EXTENSION;
        }
        filesExistAtPath(Arrays.asList(encPath + actualFileName));
        when(osDao.fileIsFile(any(File.class))).thenReturn(true);
        when(osDao.getFileExtension(encFileName)).thenReturn(Config.ENC_FILE_EXTENSION);
        when(osDao.getFileExtension(any(File.class))).thenReturn(Config.ENC_FILE_EXTENSION);
        List<String> args = new ArrayList<>();
        args.add(encFileName);
        if (silentFlag) {
            args.add(Decrypt.SILENT_FLAG);
        }
        decrypt.execute(args);
        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);

        verify(settings).getValue(Config.Settings.ENCDIR);
        verify(cryptoHelper).decryptFile(fileCaptor.capture(), eq(silentFlag));
        Assert.assertEquals(actualFileName, fileCaptor.getValue().getName());
    }

    private void decryptEnc(String encFileName, List<String> args, boolean silentFlag) {
        filesExistAtPath(Arrays.asList(encFileName));
        when(osDao.fileIsFile(any(File.class))).thenReturn(true);
        when(osDao.getFileExtension(encFileName)).thenReturn(Config.ENC_FILE_EXTENSION);
        when(osDao.getFileExtension(any(File.class))).thenReturn(Config.ENC_FILE_EXTENSION);
        decrypt.execute(args);
        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
        verify(cryptoHelper).decryptFile(fileCaptor.capture(), eq(silentFlag));
        Assert.assertEquals(encFileName, fileCaptor.getValue().getName());
    }

    private void decryptLog(String logFileName, boolean silentFlag) {
        filesExistAtPath(Arrays.asList(logFileName));
        when(osDao.fileIsFile(any(File.class))).thenReturn(true);
        when(osDao.getFileExtension(logFileName)).thenReturn(Config.LOG_FILE_EXTENSION);
        when(osDao.getFileExtension(any(File.class))).thenReturn(Config.LOG_FILE_EXTENSION);
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
        Assert.assertEquals(logFileName, fileCaptor.getValue().getName());
    }
}
