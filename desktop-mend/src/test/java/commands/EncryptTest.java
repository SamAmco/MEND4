package commands;

import co.samco.mend4.core.Settings;
import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.desktop.commands.Encrypt;
import co.samco.mend4.desktop.exception.SettingRequiredException;
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
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.*;

public class EncryptTest extends TestBase {
    private Encrypt encrypt;

    @Before
    public void setup() {
        super.setup();
        encrypt = new Encrypt(settings, settingsHelper, log, strings, cryptoHelper, inputHelper, fileResolveHelper);
    }

    @Test
    public void testCommandWithSettingsDependencies() throws IOException, SettingRequiredException {
        super.testCommandWithSettingsDependencies(encrypt);
    }

    private void setLogAndEncDir() throws IOException {
        when(settings.valueSet(Settings.Name.ENCDIR)).thenReturn(true);
        when(settings.valueSet(Settings.Name.LOGDIR)).thenReturn(true);
    }

    @Test
    public void encryptViaTextEditor() throws IOException, NoSuchPaddingException,
            NoSuchAlgorithmException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException,
            BadPaddingException, CorruptSettingsException, InvalidKeySpecException {
        setLogAndEncDir();
        encrypt.execute(Collections.emptyList());
        verify(inputHelper).createInputProviderAndRegisterListener(encrypt);
        encrypt.onWrite("hi".toCharArray());
        verify(cryptoHelper).encryptTextToLog("hi".toCharArray(), false);
    }

    @Test
    public void encryptViaTextEditorAppend() throws IOException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException,
            CorruptSettingsException, InvalidKeySpecException {
        setLogAndEncDir();
        encrypt.execute(Arrays.asList(Encrypt.APPEND_FLAG));
        verify(inputHelper).createInputProviderAndRegisterListener(encrypt);
        encrypt.onWrite("hi".toCharArray());
        verify(cryptoHelper).encryptTextToLog("hi".toCharArray(), true);
    }

    @Test
    public void encryptFromArg() throws IOException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException,
            CorruptSettingsException, InvalidKeySpecException {
        setLogAndEncDir();
        encrypt.execute(Arrays.asList(Encrypt.FROM_ARG_FLAG, "hi"));
        verify(cryptoHelper).encryptTextToLog("hi".toCharArray(), false);
    }

    @Test
    public void encryptFromArgAppend() throws IOException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException,
            CorruptSettingsException, InvalidKeySpecException {
        setLogAndEncDir();
        encrypt.execute(Arrays.asList(Encrypt.APPEND_FLAG, Encrypt.FROM_ARG_FLAG, "hi"));
        verify(cryptoHelper).encryptTextToLog("hi".toCharArray(), true);
    }

    @Test
    public void encryptFromArgAppendFlipped() throws IOException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException,
            CorruptSettingsException, InvalidKeySpecException {
        setLogAndEncDir();
        encrypt.execute(Arrays.asList(Encrypt.FROM_ARG_FLAG, Encrypt.APPEND_FLAG, "hi"));
        verify(cryptoHelper).encryptTextToLog("hi".toCharArray(), true);
    }

    @Test
    public void encryptFromArgMalformed() throws IOException {
        setLogAndEncDir();
        encrypt.execute(Arrays.asList(Encrypt.FROM_ARG_FLAG, "a", "b"));
        verify(err).println(strings.getf("Encrypt.badDataArgs", Encrypt.FROM_ARG_FLAG));
    }

    @Test
    public void encryptTooManyArgs() throws IOException {
        setLogAndEncDir();
        encrypt.execute(Arrays.asList("a", "b", "c"));
        verify(err).println(strings.getf("General.invalidArgNum", Encrypt.COMMAND_NAME));
    }

    @Test
    public void encryptFile() throws IOException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException,
            CorruptSettingsException, InvalidKeySpecException {
        setLogAndEncDir();
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
        setLogAndEncDir();
        String fileName1 = "hi1";
        String fileName2 = "hi2";
        when(fileResolveHelper.resolveFile(eq(fileName1))).thenReturn(new File(fileName1));
        encrypt.execute(Arrays.asList(fileName1, fileName2));
        ArgumentCaptor<File> outCaptor = ArgumentCaptor.forClass(File.class);
        verify(cryptoHelper).encryptFile(outCaptor.capture(), eq(fileName2));
        Assert.assertTrue(outCaptor.getValue().getName().equals(fileName1));
    }
}
