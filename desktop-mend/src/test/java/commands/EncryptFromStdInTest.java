package commands;

import co.samco.mend4.core.OSDao;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.desktop.commands.Encrypt;
import co.samco.mend4.desktop.commands.EncryptFromStdIn;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.helper.CryptoHelper;
import co.samco.mend4.desktop.helper.FileResolveHelper;
import co.samco.mend4.desktop.helper.InputHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class EncryptFromStdInTest {
    private EncryptFromStdIn encrypt;
    private Settings settings;
    private InputHelper inputHelper;
    private CryptoHelper cryptoHelper;
    private FileResolveHelper fileResolveHelper;
    private PrintStreamProvider printStreamProvider;
    private I18N strings;
    private OSDao osDao;

    @Before
    public void setup() throws IOException {
        settings = mock(Settings.class);
        osDao = mock(OSDao.class);
        strings = new I18N("en", "UK");
        printStreamProvider = mock(PrintStreamProvider.class);
        cryptoHelper = mock(CryptoHelper.class);
        fileResolveHelper = mock(FileResolveHelper.class);
        inputHelper = mock(InputHelper.class);
        when(settings.valueSet(Settings.Name.ENCDIR)).thenReturn(true);
        when(settings.valueSet(Settings.Name.LOGDIR)).thenReturn(true);
        encrypt = new EncryptFromStdIn(settings, printStreamProvider, strings, cryptoHelper, inputHelper, osDao, fileResolveHelper);
    }

    @Test
    public void encryptFromStdIn() throws IOException, NoSuchAlgorithmException, InvalidKeyException,
            InvalidAlgorithmParameterException, NoSuchPaddingException, BadPaddingException, CorruptSettingsException,
            InvalidKeySpecException, IllegalBlockSizeException {
        encryptFromStdIn(false);
    }

    @Test
    public void encryptFromStdInAppend() throws IOException, NoSuchAlgorithmException, InvalidKeyException,
            InvalidAlgorithmParameterException, NoSuchPaddingException, BadPaddingException, CorruptSettingsException,
            InvalidKeySpecException, IllegalBlockSizeException {
        encryptFromStdIn(true);
    }

    public void encryptFromStdIn(boolean appendFlag) throws IOException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException,
            CorruptSettingsException, InvalidKeySpecException {
        String input = "hi\rhi again\nhi once more";
        InputStream inputStream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        when(osDao.getStdIn()).thenReturn(inputStream);
        encrypt.execute(appendFlag ? Arrays.asList(Encrypt.APPEND_FLAG) : Collections.emptyList());
        ArgumentCaptor<char[]> encryptOutput = ArgumentCaptor.forClass(char[].class);
        verify(osDao).getStdIn();
        verify(cryptoHelper).encryptTextToLog(encryptOutput.capture(), eq(appendFlag));
        String expectedText = "hi" + System.getProperty("line.separator") + "hi again" + System.getProperty("line.separator") + "hi once more";
        Assert.assertEquals(expectedText, new String(encryptOutput.getValue()));
    }

}




































