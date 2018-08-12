package commands;

import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.desktop.commands.Unlock;
import co.samco.mend4.desktop.exception.SettingRequiredException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Collections;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class UnlockTest extends TestBase {
    private Unlock unlock;

    private ArgumentCaptor<String> errCaptor;

    private String privateKeyPath = "privateKeyPath";
    private String publicKeyPath = "publicKeyPath";

    @Before
    public void setup() {
        super.setup();
        errCaptor = ArgumentCaptor.forClass(String.class);
        when(fileResolveHelper.getPrivateKeyPath()).thenReturn(privateKeyPath);
        when(fileResolveHelper.getPublicKeyPath()).thenReturn(publicKeyPath);
        unlock = new Unlock(strings, osDao, settings, settingsHelper, log, cryptoProvider, shredHelper, fileResolveHelper, keyHelper);
    }

    @Test
    public void testCommandWithSettingsDependencies() throws IOException, SettingRequiredException {
        super.testCommandWithSettingsDependencies(unlock);
    }

    @Test
    public void wrongPassword() throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException,
            IllegalBlockSizeException, BadPaddingException, InvalidKeySpecException, IOException, CorruptSettingsException {
        when(osDao.readPassword(anyString())).thenReturn(new char[0]);
        when(keyHelper.getPublicKey()).thenReturn(null);
        when(cryptoProvider.checkPassword(any(char[].class), any(String.class), any(RSAPublicKey.class)))
                .thenReturn(false);
        unlock.execute(Collections.emptyList());
        verify(err).println(eq(strings.get("Unlock.incorrectPassword")));
        verify(osDao, never()).fileExists(any());
        verify(shredHelper, never()).tryShredFile(any());
        verify(osDao, never()).writeDataToFile(any(), any());
    }

    @Test
    public void correctPasswordNoExistingKeys() throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException,
            IllegalBlockSizeException, BadPaddingException, InvalidKeySpecException, IOException, CorruptSettingsException {
        correctPasswordTest();
        verify(shredHelper, never()).tryShredFile(any());
    }

    @Test
    public void correctPasswordExistingKeys() throws IOException, NoSuchAlgorithmException, InvalidKeyException,
            NoSuchPaddingException, BadPaddingException, CorruptSettingsException, InvalidKeySpecException,
            IllegalBlockSizeException {
        when(osDao.fileExists(any())).thenReturn(true);
        correctPasswordTest();
        verify(shredHelper, times(2)).tryShredFile(any());
    }

    private void correctPasswordTest() throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException,
            IllegalBlockSizeException, BadPaddingException, InvalidKeySpecException,
            IOException, CorruptSettingsException {
        String password = "password";
        when(osDao.readPassword(anyString())).thenReturn(password.toCharArray());
        when(keyHelper.getPublicKey()).thenReturn(null);
        when(cryptoProvider.checkPassword(any(char[].class), any(String.class), any(RSAPublicKey.class)))
                .thenReturn(true);
        unlock.execute(Collections.emptyList());
        verify(osDao, times(2)).writeDataToFile(any(), any());
    }
}
