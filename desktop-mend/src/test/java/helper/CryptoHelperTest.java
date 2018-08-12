package helper;

import co.samco.mend4.core.AppProperties;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.core.exception.MalformedLogFileException;
import co.samco.mend4.core.util.LogUtils;
import co.samco.mend4.desktop.helper.CryptoHelper;
import commands.TestBase;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.File;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class CryptoHelperTest extends TestBase {

    private String encdir = File.separatorChar + "output" + File.separatorChar + "directory";

    @Before
    public void setup() {
        super.setup();
        cryptoHelper = new CryptoHelper(strings, log, fileResolveHelper, settings, cryptoProvider,
                keyHelper, osDao, versionHelper);
    }

    @Test
    public void testEncInputOutputFilesCorrect() throws IOException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException,
            CorruptSettingsException, InvalidKeySpecException {
        String inputFileName = "/input.txt";
        File inputFile = new File(inputFileName);
        when(settings.getValue(Settings.Name.ENCDIR)).thenReturn(encdir);
        cryptoHelper.encryptFile(inputFile, null);

        ArgumentCaptor<File> fileCaptor1 = ArgumentCaptor.forClass(File.class);
        verify(osDao).getInputStreamForFile(fileCaptor1.capture());
        Assert.assertEquals(inputFileName, fileCaptor1.getValue().getAbsolutePath());

        ArgumentCaptor<File> fileCaptor2 = ArgumentCaptor.forClass(File.class);
        verify(osDao).getOutputStreamForFile(fileCaptor2.capture());
        Pattern pattern = Pattern.compile(encdir + File.separatorChar + "\\d{17}." + AppProperties.ENC_FILE_EXTENSION);
        Matcher matcher = pattern.matcher(fileCaptor2.getValue().getAbsolutePath());
        Assert.assertTrue(matcher.matches());

        verify(cryptoProvider).encryptEncStream(eq(null), any(), any(), eq("txt"));
    }

    @Test
    public void testEncInputFileNoExtension() throws IOException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException,
            CorruptSettingsException, InvalidKeySpecException {
        String inputFileName = "/input";
        File inputFile = new File(inputFileName);
        when(settings.getValue(Settings.Name.ENCDIR)).thenReturn(encdir);
        cryptoHelper.encryptFile(inputFile, null);
        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
        verify(osDao).getInputStreamForFile(fileCaptor.capture());
        Assert.assertEquals(inputFileName, fileCaptor.getValue().getAbsolutePath());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDontOverwriteEncFile() throws IOException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException,
            CorruptSettingsException, InvalidKeySpecException {
        File inputFile = new File("input");
        when(settings.getValue(Settings.Name.ENCDIR)).thenReturn(encdir);
        doThrow(new IllegalArgumentException()).when(fileResolveHelper).assertFileDoesNotExist(any());
        cryptoHelper.encryptFile(inputFile, null);
    }

    @Test
    public void testEncEmptyLog() throws IOException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
            InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException, CorruptSettingsException,
            InvalidKeySpecException {
        cryptoHelper.encryptTextToLog(new char[0], true);
        verify(fileResolveHelper, never()).getCurrentLogFile();
        verify(cryptoProvider, never()).encryptLogStream(any(), any(), any());
    }

    @Test
    public void testLogEncrypted() throws IOException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
            InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException, CorruptSettingsException,
            InvalidKeySpecException {
        String message = "hi";
        File logFile = new File("/currentLogFile." + AppProperties.LOG_FILE_EXTENSION);
        when(fileResolveHelper.getCurrentLogFile()).thenReturn(logFile);
        cryptoHelper.encryptTextToLog(message.toCharArray(), true);
        verify(osDao).createNewFile(any());
        verify(osDao).getOutputStreamForFile(eq(logFile), eq(true));
        verify(cryptoProvider).encryptLogStream(any(), eq(message), any());
    }

    @Test
    public void testHeaderAddedToLog() throws IOException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
            InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException, CorruptSettingsException,
            InvalidKeySpecException {
        String version = "ver";
        String message = "hi";
        String messageWithHeader = LogUtils.addHeaderToLogText(message, strings.get("Platform.header"), version, "\n");
        File logFile = new File("/currentLogFile." + AppProperties.LOG_FILE_EXTENSION);
        when(versionHelper.getVersion()).thenReturn(version);
        when(fileResolveHelper.getCurrentLogFile()).thenReturn(logFile);
        cryptoHelper.encryptTextToLog(message.toCharArray(), false);
        verify(cryptoProvider).encryptLogStream(any(), eq(messageWithHeader), any());
    }

    @Test
    public void testDecryptLog() throws IOException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
            InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException, MalformedLogFileException,
            InvalidKeySpecException {
        File logFile = new File("/logfile.log");
        cryptoHelper.decryptLog(logFile);
        verify(osDao).getInputStreamForFile(eq(logFile));
        verify(cryptoProvider).decryptLogStream(any(), any(), eq(out));
    }

    @Test
    public void testDecryptFile() throws IOException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
            InvalidAlgorithmParameterException, MalformedLogFileException, BadPaddingException, CorruptSettingsException,
            InvalidKeySpecException, IllegalBlockSizeException {
        String encFileName = "encFile";
        String fileExtension = "txt";
        File encFile = new File(File.separator + "path" + File.separator + encFileName + "." + AppProperties.ENC_FILE_EXTENSION);
        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
        String decDir = File.separator + "decdir";
        when(settings.getValue(Settings.Name.DECDIR)).thenReturn(decDir);
        when(cryptoProvider.decryptEncStream(any(), any(), any())).thenReturn(fileExtension);
        cryptoHelper.decryptFile(encFile, false);
        verify(fileResolveHelper).assertDirWritable(decDir);
        verify(fileResolveHelper, times(2)).assertFileDoesNotExist(fileCaptor.capture());
        Assert.assertEquals(fileCaptor.getAllValues().get(0).getAbsolutePath(),
                decDir + File.separatorChar + encFileName);
        Assert.assertEquals(fileCaptor.getAllValues().get(1).getAbsolutePath(),
                decDir + File.separatorChar + encFileName + "." + fileExtension);
        verify(cryptoProvider).decryptEncStream(any(), any(), any());
        verify(osDao).renameFile(any(), eq(encFileName + "." + fileExtension));
        verify(osDao).desktopOpenFile(any());
    }
}
























