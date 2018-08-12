package commands;

import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.core.Settings;
import co.samco.mend4.desktop.commands.Clean;
import co.samco.mend4.desktop.exception.SettingRequiredException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

import static org.mockito.Mockito.*;

public class CleanTest extends TestBase {
    private Clean clean;

    @Before
    public void setup() {
        super.setup();
        clean = new Clean(strings, log, settingsHelper, shredHelper, settings);
    }

    @Test
    public void testCommandWithSettingsDependencies() throws IOException, SettingRequiredException {
        super.testCommandWithSettingsDependencies(clean);
    }

    @Test
    public void executesShredCommand() throws IOException, CorruptSettingsException {
        String decDir = "dir";
        when(settings.getValue(eq(Settings.Name.DECDIR))).thenReturn(decDir);
        clean.execute(Collections.emptyList());
        verify(shredHelper).shredFilesInDirectory(eq(decDir));
        verify(err).println(eq(strings.get("Clean.cleanComplete")));
    }

    @Test
    public void printsException() throws CorruptSettingsException, IOException {
        String exception = "hi";
        when(settings.getValue(eq(Settings.Name.DECDIR))).thenThrow(new CorruptSettingsException(exception, ""));
        clean.execute(Collections.emptyList());
        verify(err).println(eq(exception));
    }
}
