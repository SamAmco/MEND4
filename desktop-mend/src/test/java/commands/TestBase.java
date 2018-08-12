package commands;

import co.samco.mend4.core.OSDao;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.crypto.CryptoProvider;
import co.samco.mend4.desktop.commands.Command;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.exception.SettingRequiredException;
import co.samco.mend4.desktop.helper.*;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import org.junit.Assert;
import org.junit.Before;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;

import static org.mockito.Mockito.*;

public class TestBase {
    protected CryptoHelper cryptoHelper;
    protected CryptoProvider cryptoProvider;
    protected FileResolveHelper fileResolveHelper;
    protected InputHelper inputHelper;
    protected KeyHelper keyHelper;
    protected MergeHelper mergeHelper;
    protected SettingsHelper settingsHelper;
    protected ShredHelper shredHelper;
    protected VersionHelper versionHelper;
    protected OSDao osDao;
    protected I18N strings;
    protected PrintStreamProvider log;
    protected PrintStream out;
    protected PrintStream err;
    protected Settings settings;

    @Before
    public void setup() {
        strings = new I18N("en", "UK");
        err = mock(PrintStream.class);
        out = mock(PrintStream.class);
        log = mock(PrintStreamProvider.class);
        when(log.err()).thenReturn(err);
        when(log.out()).thenReturn(out);
        osDao = mock(OSDao.class);
        cryptoHelper = mock(CryptoHelper.class);
        cryptoProvider = mock(CryptoProvider.class);
        fileResolveHelper = mock(FileResolveHelper.class);
        inputHelper = mock(InputHelper.class);
        keyHelper = mock(KeyHelper.class);
        mergeHelper = mock(MergeHelper.class);
        settingsHelper = mock(SettingsHelper.class);
        shredHelper = mock(ShredHelper.class);
        versionHelper = mock(VersionHelper.class);
        settings = mock(Settings.class);
    }

    protected void testCommandWithSettingsDependencies(Command command) throws IOException, SettingRequiredException {
        String message = "message";
        doThrow(new SettingRequiredException(message)).when(settingsHelper).assertRequiredSettingsExist(any(), any());
        command.executeCommand(new ArrayList<>());
        verify(err).println(eq(message));
        Assert.assertNotEquals(0, command.getExecutionResult());
    }
}
