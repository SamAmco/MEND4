package commands;

import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.desktop.commands.Encrypt;
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
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.*;

public class EncryptTest {
    private Encrypt encrypt;
    private InputHelper inputHelper;
    private CryptoHelper cryptoHelper;
    private FileResolveHelper fileResolveHelper;
    private PrintStreamProvider printStreamProvider;
    private PrintStream err;
    private PrintStream out;
    private I18N strings;

    @Before
    public void setup() {
        strings = new I18N("en", "UK");
        err = mock(PrintStream.class);
        out = mock(PrintStream.class);
        printStreamProvider = mock(PrintStreamProvider.class);
        fileResolveHelper = mock(FileResolveHelper.class);
        when(printStreamProvider.err()).thenReturn(err);
        when(printStreamProvider.out()).thenReturn(out);
        cryptoHelper = mock(CryptoHelper.class);
        inputHelper = mock(InputHelper.class);
        encrypt = new Encrypt(printStreamProvider, strings, cryptoHelper, inputHelper, fileResolveHelper);
    }

    @Test
    public void encryptViaTextEditor() throws IOException, NoSuchPaddingException,
            NoSuchAlgorithmException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException,
            BadPaddingException, CorruptSettingsException, InvalidKeySpecException {
        encrypt.execute(Collections.emptyList());
        verify(inputHelper).createInputProviderAndRegisterListener(encrypt);
        encrypt.onWrite("hi".toCharArray());
        verify(cryptoHelper).encryptTextToLog("hi".toCharArray(), false);
    }

    @Test
    public void encryptViaTextEditorAppend() throws IOException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException,
            CorruptSettingsException, InvalidKeySpecException {
        encrypt.execute(Arrays.asList(Encrypt.APPEND_FLAG));
        verify(inputHelper).createInputProviderAndRegisterListener(encrypt);
        encrypt.onWrite("hi".toCharArray());
        verify(cryptoHelper).encryptTextToLog("hi".toCharArray(), true);
    }

    @Test
    public void encryptFromArg() throws IOException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException,
            CorruptSettingsException, InvalidKeySpecException {
        encrypt.execute(Arrays.asList(Encrypt.FROM_ARG_FLAG, "hi"));
        verify(cryptoHelper).encryptTextToLog("hi".toCharArray(), false);
    }

    @Test
    public void encryptFromArgAppend() throws IOException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException,
            CorruptSettingsException, InvalidKeySpecException {
        encrypt.execute(Arrays.asList(Encrypt.APPEND_FLAG, Encrypt.FROM_ARG_FLAG, "hi"));
        verify(cryptoHelper).encryptTextToLog("hi".toCharArray(), true);
    }

    @Test
    public void encryptFromArgAppendFlipped() throws IOException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException,
            CorruptSettingsException, InvalidKeySpecException {
        encrypt.execute(Arrays.asList(Encrypt.FROM_ARG_FLAG, Encrypt.APPEND_FLAG, "hi"));
        verify(cryptoHelper).encryptTextToLog("hi".toCharArray(), true);
    }

    @Test
    public void encryptFromArgMalformed() {
        encrypt.execute(Arrays.asList(Encrypt.FROM_ARG_FLAG, "a", "b"));
        verify(err).println(strings.getf("Encrypt.badDataArgs", Encrypt.FROM_ARG_FLAG));
    }

    @Test
    public void encryptTooManyArgs() {
        encrypt.execute(Arrays.asList("a", "b", "c"));
        verify(err).println(strings.getf("General.invalidArgNum", Encrypt.COMMAND_NAME));
    }

    @Test
    public void encryptFile() throws IOException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException,
            CorruptSettingsException, InvalidKeySpecException {
        String fileName = "hi";
        when(fileResolveHelper.resolveFile(eq(fileName))).thenReturn(new File(fileName));
        encrypt.execute(Arrays.asList(fileName));
        ArgumentCaptor<File> outCaptor = ArgumentCaptor.forClass(File.class);
        verify(cryptoHelper).encryptFile(outCaptor.capture(), (String)isNull());
        Assert.assertTrue(outCaptor.getValue().getName().equals(fileName));
    }

    @Test
    public void encryptFileWithName() throws IOException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException,
            CorruptSettingsException, InvalidKeySpecException {
        String fileName1 = "hi1";
        String fileName2 = "hi2";
        when(fileResolveHelper.resolveFile(eq(fileName1))).thenReturn(new File(fileName1));
        encrypt.execute(Arrays.asList(fileName1, fileName2));
        ArgumentCaptor<File> outCaptor = ArgumentCaptor.forClass(File.class);
        verify(cryptoHelper).encryptFile(outCaptor.capture(), eq(fileName2));
        Assert.assertTrue(outCaptor.getValue().getName().equals(fileName1));
    }
}
