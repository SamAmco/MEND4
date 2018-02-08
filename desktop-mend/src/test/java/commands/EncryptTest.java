package commands;

import co.samco.mend4.desktop.commands.Encrypt;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.helper.EncryptHelper;
import co.samco.mend4.desktop.helper.InputHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import org.junit.Before;
import org.junit.Test;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.*;

public class EncryptTest {
    private Encrypt encrypt;
    private InputHelper inputHelper;
    private EncryptHelper encryptHelper;
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
        when(printStreamProvider.err()).thenReturn(err);
        when(printStreamProvider.out()).thenReturn(out);
        encryptHelper = mock(EncryptHelper.class);
        inputHelper = mock(InputHelper.class);
        encrypt = new Encrypt(printStreamProvider, strings, encryptHelper, inputHelper);
    }

    @Test
    public void encryptViaTextEditor() {
        encrypt.execute(Collections.emptyList());
        verify(inputHelper).createInputProviderAndRegisterListener(encrypt);
        encrypt.onWrite("hi".toCharArray());
        verify(encryptHelper).encryptTextToLog("hi".toCharArray(), false);
    }

    @Test
    public void encryptViaTextEditorAppend() {
        encrypt.execute(Arrays.asList(Encrypt.APPEND_FLAG));
        verify(inputHelper).createInputProviderAndRegisterListener(encrypt);
        encrypt.onWrite("hi".toCharArray());
        verify(encryptHelper).encryptTextToLog("hi".toCharArray(), true);
    }

    @Test
    public void encryptFromArg() {
        encrypt.execute(Arrays.asList(Encrypt.FROM_ARG_FLAG, "hi"));
        verify(encryptHelper).encryptTextToLog("hi".toCharArray(), false);
    }

    @Test
    public void encryptFromArgAppend() {
        encrypt.execute(Arrays.asList(Encrypt.APPEND_FLAG, Encrypt.FROM_ARG_FLAG, "hi"));
        verify(encryptHelper).encryptTextToLog("hi".toCharArray(), true);
    }

    @Test
    public void encryptFromArgAppendFlipped() {
        encrypt.execute(Arrays.asList(Encrypt.FROM_ARG_FLAG, Encrypt.APPEND_FLAG, "hi"));
        verify(encryptHelper).encryptTextToLog("hi".toCharArray(), true);
    }

    @Test
    public void encryptFromArgMalformed() {
        encrypt.execute(Arrays.asList(Encrypt.FROM_ARG_FLAG, "a", "b"));
        verify(err).println(strings.getf("Encrypt.badDataArgs", Encrypt.FROM_ARG_FLAG));
    }

    @Test
    public void encryptTooManyArgs() {
        encrypt.execute(Arrays.asList("a", "b", "c"));
        verify(err).println(strings.getf("Encrypt.invalidArgNum", Encrypt.COMMAND_NAME));
    }

    @Test
    public void encryptFile() {
        encrypt.execute(Arrays.asList("hi"));
        verify(encryptHelper).encryptFile("hi", null);
    }

    @Test
    public void encryptFileWithName() {
        encrypt.execute(Arrays.asList("a", "b"));
        verify(encryptHelper).encryptFile("a", "b");
    }
}
