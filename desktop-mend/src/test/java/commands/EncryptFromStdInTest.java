package commands;

import co.samco.mend4.desktop.commands.Encrypt;
import co.samco.mend4.desktop.commands.EncryptFromStdIn;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.dao.OSDao;
import co.samco.mend4.desktop.helper.CryptoHelper;
import co.samco.mend4.desktop.helper.InputHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EncryptFromStdInTest {
    private EncryptFromStdIn encrypt;
    private InputHelper inputHelper;
    private CryptoHelper cryptoHelper;
    private PrintStreamProvider printStreamProvider;
    private I18N strings;
    private OSDao osDao;

    @Before
    public void setup() {
        osDao = mock(OSDao.class);
        strings = new I18N("en", "UK");
        printStreamProvider = mock(PrintStreamProvider.class);
        cryptoHelper = mock(CryptoHelper.class);
        inputHelper = mock(InputHelper.class);
        encrypt = new EncryptFromStdIn(printStreamProvider, strings, cryptoHelper, inputHelper, osDao);
    }

    @Test
    public void encryptFromStdIn() throws UnsupportedEncodingException {
        encryptFromStdIn(false);
    }

    @Test
    public void encryptFromStdInAppend() throws UnsupportedEncodingException {
        encryptFromStdIn(true);
    }

    public void encryptFromStdIn(boolean appendFlag) throws UnsupportedEncodingException {
        String input = "hi\rhi again\nhi once more";
        InputStream inputStream = new ByteArrayInputStream(input.getBytes("UTF-8"));
        when(osDao.getStdIn()).thenReturn(inputStream);
        encrypt.execute(appendFlag ? Arrays.asList(Encrypt.APPEND_FLAG) : Collections.emptyList());
        ArgumentCaptor<char[]> encryptOutput = ArgumentCaptor.forClass(char[].class);
        verify(osDao).getStdIn();
        verify(cryptoHelper).encryptTextToLog(encryptOutput.capture(), eq(appendFlag));
        String expectedText = "hi" + System.getProperty("line.separator") + "hi again" + System.getProperty("line.separator") + "hi once more";
        Assert.assertEquals(expectedText, new String(encryptOutput.getValue()));
    }

}




































