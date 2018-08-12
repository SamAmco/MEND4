package commands;

import co.samco.mend4.core.AppProperties;
import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.core.exception.MalformedLogFileException;
import co.samco.mend4.desktop.commands.Decrypt;
import co.samco.mend4.desktop.exception.MendLockedException;
import co.samco.mend4.desktop.exception.SettingRequiredException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class DecryptTest extends TestBase {
    private Decrypt decrypt;

    @Before
    public void setup() {
        super.setup();
        decrypt = new Decrypt(log, strings, settingsHelper, cryptoHelper, osDao, fileResolveHelper);
    }

    @Test
    public void testCommandWithSettingsDependencies() throws IOException, SettingRequiredException {
        super.testCommandWithSettingsDependencies(decrypt);
    }

    @Test
    public void decryptLog() throws IOException, CorruptSettingsException, MalformedLogFileException, InvalidKeyException,
            NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException,
            InvalidAlgorithmParameterException, InvalidKeySpecException, MendLockedException {
        String logFileName = "logFile." + AppProperties.LOG_FILE_EXTENSION;
        decryptLog(logFileName, false);
    }

    @Test
    public void decryptLogWithSilentFlag() throws IOException, CorruptSettingsException, MalformedLogFileException,
            InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException,
            NoSuchPaddingException, InvalidAlgorithmParameterException, InvalidKeySpecException, MendLockedException {
        String logFileName = "logFile." + AppProperties.LOG_FILE_EXTENSION;
        decryptLog(logFileName, true);
    }

    @Test
    public void decryptEnc() throws IOException, CorruptSettingsException, MalformedLogFileException,
            InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchPaddingException, BadPaddingException,
            IllegalBlockSizeException, InvalidKeyException, InvalidKeySpecException, MendLockedException {
        String encFileName = "sam." + AppProperties.ENC_FILE_EXTENSION;
        decryptEnc(encFileName, Arrays.asList(encFileName), false);
    }

    @Test
    public void decryptEncSilent() throws IOException, CorruptSettingsException, MalformedLogFileException,
            InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchPaddingException, BadPaddingException,
            IllegalBlockSizeException, InvalidKeyException, InvalidKeySpecException, MendLockedException {
        String encFileName = "sam." + AppProperties.ENC_FILE_EXTENSION;
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
    public void encFileDoesntExist() throws IOException, CorruptSettingsException {
        String fileName = "unkown.extension";
        when(fileResolveHelper.resolveAsEncFilePath(fileName)).thenReturn(new File(fileName));
        when(fileResolveHelper.fileExistsAndHasExtension(anyString(), any(File.class))).thenReturn(false);
        decrypt.execute(Arrays.asList(fileName));
        verify(fileResolveHelper).resolveAsLogFilePath(eq(fileName));
    }

    @Test
    public void noArgs() {
        decrypt.execute(Collections.emptyList());
        ArgumentCaptor<String> errCapture = ArgumentCaptor.forClass(String.class);
        verify(err, times(2)).println(errCapture.capture());
        Assert.assertEquals(strings.get("Decrypt.noFile"), errCapture.getAllValues().get(0));
        Assert.assertEquals(strings.getf("Decrypt.usage", Decrypt.COMMAND_NAME, Decrypt.SILENT_FLAG), errCapture.getAllValues().get(1));
    }

    private void decryptEnc(String encFileName, List<String> args, boolean silentFlag) throws IOException, CorruptSettingsException,
            MalformedLogFileException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, BadPaddingException,
            IllegalBlockSizeException, InvalidAlgorithmParameterException, InvalidKeySpecException, MendLockedException {
        when(fileResolveHelper.resolveAsEncFilePath(encFileName)).thenReturn(new File(encFileName));
        when(fileResolveHelper.fileExistsAndHasExtension(eq(AppProperties.ENC_FILE_EXTENSION), any(File.class))).thenReturn(true);
        decrypt.execute(args);
        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
        verify(fileResolveHelper).fileExistsAndHasExtension(eq(AppProperties.ENC_FILE_EXTENSION), any(File.class));
        verify(cryptoHelper).decryptFile(fileCaptor.capture(), eq(silentFlag));
        Assert.assertEquals(encFileName, fileCaptor.getValue().getName());
    }

    private void decryptLog(String logFileName, boolean silentFlag) throws IOException, CorruptSettingsException,
            MalformedLogFileException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, IllegalBlockSizeException,
            BadPaddingException, NoSuchPaddingException, InvalidKeyException, InvalidKeySpecException, MendLockedException {
        when(fileResolveHelper.resolveAsLogFilePath(anyString())).thenReturn(new File(logFileName));
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
