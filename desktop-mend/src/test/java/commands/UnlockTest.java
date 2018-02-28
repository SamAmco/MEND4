package commands;

import co.samco.mend4.core.AppProperties;
import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.core.OSDao;
import co.samco.mend4.core.Settings;
import co.samco.mend4.desktop.commands.Unlock;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.helper.CryptoHelper;
import co.samco.mend4.desktop.helper.FileResolveHelper;
import co.samco.mend4.desktop.helper.ShredHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Collections;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class UnlockTest {
    private Unlock unlock;
    private I18N strings;
    private OSDao osDao;
    private Settings settings;
    private PrintStream out;
    private PrintStream err;
    private PrintStreamProvider log;
    private CryptoHelper cryptoHelper;
    private ShredHelper shredHelper;
    private FileResolveHelper fileResolveHelper;

    private ArgumentCaptor<String> errCaptor;

    private String privateKeyPath = "privateKeyPath";
    private String publicKeyPath = "publicKeyPath";

    @Before
    public void setup() {
        errCaptor = ArgumentCaptor.forClass(String.class);
        err = mock(PrintStream.class);
        out = mock(PrintStream.class);
        log = mock(PrintStreamProvider.class);
        when(log.err()).thenReturn(err);
        when(log.out()).thenReturn(out);
        strings = new I18N("en", "UK");
        settings = mock(Settings.class);
        cryptoHelper = mock(CryptoHelper.class);
        osDao = mock(OSDao.class);
        shredHelper = mock(ShredHelper.class);
        fileResolveHelper = mock(FileResolveHelper.class);
        when(fileResolveHelper.getPrivateKeyPath()).thenReturn(privateKeyPath);
        when(fileResolveHelper.getPublicKeyPath()).thenReturn(publicKeyPath);
        unlock = new Unlock(strings, osDao, settings, log, cryptoHelper, shredHelper, fileResolveHelper);
    }

    @Test
    public void wrongPassword() throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException,
            IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, InvalidKeySpecException,
            IOException, CorruptSettingsException {
        when(osDao.readPassword(anyString())).thenReturn(new char[0]);
        when(cryptoHelper.decryptBytesWithPassword(any(byte[].class), any(char[].class)))
                .thenReturn("Not the passcheck text".getBytes(StandardCharsets.UTF_8));
        unlock.execute(Collections.emptyList());
        verify(err).println(eq(strings.get("Unlock.incorrectPassword")));
        verify(osDao, never()).fileExists(any());
        verify(shredHelper, never()).tryShredFile(any());
        verify(osDao, never()).writeDataToFile(any(), any());
    }

    @Test
    public void correctPasswordNoExistingKeys() throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException,
            IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, InvalidKeySpecException,
            IOException, CorruptSettingsException {
        correctPasswordTest();
        verify(shredHelper, never()).tryShredFile(any());
    }

    @Test
    public void correctPasswordExistingKeys() throws IOException, NoSuchAlgorithmException, InvalidKeyException,
            InvalidAlgorithmParameterException, NoSuchPaddingException, BadPaddingException,
            CorruptSettingsException, InvalidKeySpecException, IllegalBlockSizeException {
        when(osDao.fileExists(any())).thenReturn(true);
        correctPasswordTest();
        verify(shredHelper, times(2)).tryShredFile(any());
    }

    private void correctPasswordTest() throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException,
            IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, InvalidKeySpecException,
            IOException {
        String password = "password";
        when(osDao.readPassword(anyString())).thenReturn(password.toCharArray());
        when(cryptoHelper.decryptBytesWithPassword(any(byte[].class), any(char[].class)))
                .thenReturn(AppProperties.PASSCHECK_TEXT.getBytes(StandardCharsets.UTF_8));
        unlock.execute(Collections.emptyList());
        verify(osDao, times(2)).writeDataToFile(any(), any());
    }
}
