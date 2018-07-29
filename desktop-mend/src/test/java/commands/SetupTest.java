package commands;

import co.samco.mend4.core.AppProperties;
import co.samco.mend4.core.OSDao;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.bean.EncodedKeyInfo;
import co.samco.mend4.desktop.commands.Setup;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.helper.CryptoHelper;
import co.samco.mend4.desktop.helper.FileResolveHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

public class SetupTest {
    private Setup setup;

    private PrintStreamProvider log;
    private I18N strings;
    private OSDao osDao;
    private CryptoHelper cryptoHelper;
    private FileResolveHelper fileResolveHelper;
    private PrintStream err;
    private PrintStream out;
    private Settings settings;
    private ArgumentCaptor<String> errCaptor;

    private final String settingsPath = "settingspath";

    @Before
    public void setup() {
        errCaptor = ArgumentCaptor.forClass(String.class);
        err = mock(PrintStream.class);
        out = mock(PrintStream.class);
        log = mock(PrintStreamProvider.class);
        when(log.err()).thenReturn(err);
        when(log.out()).thenReturn(out);
        fileResolveHelper = mock(FileResolveHelper.class);
        strings = new I18N("en", "UK");
        settings = mock(Settings.class);
        cryptoHelper = mock(CryptoHelper.class);
        osDao = mock(OSDao.class);

        when(fileResolveHelper.getSettingsPath()).thenReturn(settingsPath);
        setup = new Setup(log, strings, osDao, cryptoHelper, fileResolveHelper, settings);
    }

    @Test
    public void alreadySetUp() {
        when(osDao.fileExists(any(File.class))).thenReturn(true);
        setup.execute(Collections.emptyList());
        verify(err).println(errCaptor.capture());
        Assert.assertEquals(strings.getf("SetupMend.alreadySetup", settingsPath, Setup.FORCE_FLAG),
                errCaptor.getValue());
    }

    @Test
    public void wrongArgNum3() {
        setup.execute(Arrays.asList("a", "b", "c"));
        verify(err).println(errCaptor.capture());
        Assert.assertEquals(strings.getf("General.invalidArgNum", Setup.COMMAND_NAME), errCaptor.getValue());
    }

    @Test
    public void wrongArgNum1() {
        setup.execute(Arrays.asList("a"));
        verify(err).println(errCaptor.capture());
        Assert.assertEquals(strings.getf("General.invalidArgNum", Setup.COMMAND_NAME), errCaptor.getValue());
    }

    @Test
    public void passwordsDontMatch() throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
            IOException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException,
            InvalidKeySpecException  {
        String passOne = "passOne";
        String passTwo = "passTwo";
        when(osDao.readPassword(anyString())).thenAnswer(new Answer<char[]>() {
            int count = 0;
            @Override
            public char[] answer(InvocationOnMock invocation) {
                if (count++ == 0) {
                    return passOne.toCharArray();
                } else {
                    return passTwo.toCharArray();
                }
            }
        });
        EncodedKeyInfo keyInfo = new EncodedKeyInfo("a", "b", "c");
        when(cryptoHelper.getEncodedKeyInfo(anyString(), any(KeyPair.class))).thenReturn(keyInfo);
        setup.execute(Collections.emptyList());
        verify(err, times(2)).println(errCaptor.capture());
        verifySettingsSetup(keyInfo);
        Assert.assertEquals(strings.get("SetupMend.passwordMismatch"), errCaptor.getAllValues().get(0));
        Assert.assertEquals(strings.get("SetupMend.complete"), errCaptor.getAllValues().get(1));
    }

    @Test
    public void setupFromKeyFiles() throws TransformerException, NoSuchPaddingException, IOException,
            InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException,
            InvalidAlgorithmParameterException, InvalidKeySpecException {
        when(osDao.readPassword(anyString())).thenReturn("password".toCharArray());
        EncodedKeyInfo keyInfo = new EncodedKeyInfo("a", "b", "c");
        when(cryptoHelper.getEncodedKeyInfo(anyString(), any(KeyPair.class))).thenReturn(keyInfo);
        when(fileResolveHelper.resolveFile(anyString())).thenReturn(new File(""));
        setup.execute(Arrays.asList("x", "y"));
        verify(cryptoHelper).readKeyPairFromFiles(any(File.class), any(File.class));
        verify(err).println(errCaptor.capture());
        verifySettingsSetup(keyInfo);
        Assert.assertEquals(strings.get("SetupMend.complete"), errCaptor.getValue());
    }

    @Test
    public void setupFromKeyFilesNull () throws FileNotFoundException {
        String exception = "exception";
        when(osDao.readPassword(anyString())).thenReturn("password".toCharArray());
        when(fileResolveHelper.resolveFile(anyString())).thenThrow(new FileNotFoundException(exception));
        setup.execute(Arrays.asList("x", "y"));
        verify(err).println(errCaptor.capture());
        Assert.assertEquals(exception, errCaptor.getValue());
    }

    @Test
    public void setupSettingsThrowsException () throws NoSuchPaddingException, IOException, InvalidKeyException,
            NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException,
            InvalidKeySpecException {
        String exception = "exception";
        when(osDao.readPassword(anyString())).thenReturn("password".toCharArray());
        EncodedKeyInfo keyInfo = new EncodedKeyInfo("a", "b", "c");
        when(cryptoHelper.getEncodedKeyInfo(anyString(), any(KeyPair.class))).thenReturn(keyInfo);
        doThrow(new IOException(exception)).when(settings).setValue(any(Settings.Name.class), anyString());
        setup.execute(Arrays.asList("x", "y"));
        verify(err).println(errCaptor.capture());
        Assert.assertEquals(exception, errCaptor.getValue());
    }

    private void verifySettingsSetup(EncodedKeyInfo keyInfo) throws IOException {
        verify(settings).setValue(eq(Settings.Name.PREFERREDAES), eq(AppProperties.PREFERRED_AES_ALG));
        verify(settings).setValue(eq(Settings.Name.PREFERREDRSA), eq(AppProperties.PREFERRED_RSA_ALG));
        verify(settings).setValue(eq(Settings.Name.AESKEYSIZE), eq(Integer.toString(AppProperties.PREFERRED_AES_KEY_SIZE)));
        verify(settings).setValue(eq(Settings.Name.RSAKEYSIZE), eq(Integer.toString(AppProperties.PREFERRED_RSA_KEY_SIZE)));
        verify(settings).setValue(eq(Settings.Name.PRIVATEKEY), eq(keyInfo.getPrivateKey()));
        verify(settings).setValue(eq(Settings.Name.PUBLICKEY), eq(keyInfo.getPublicKey()));
        verify(settings).setValue(eq(Settings.Name.PASSCHECK), eq(keyInfo.getCipherText()));
    }

}

