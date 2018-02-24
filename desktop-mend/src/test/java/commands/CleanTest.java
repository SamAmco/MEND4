package commands;

import co.samco.mend4.core.CorruptSettingsException;
import co.samco.mend4.core.OSDao;
import co.samco.mend4.core.Settings;
import co.samco.mend4.desktop.commands.Clean;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.helper.FileResolveHelper;
import co.samco.mend4.desktop.helper.ShredHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;

import static org.mockito.Mockito.*;

public class CleanTest {
    private Clean clean;
    private OSDao osDao;
    private PrintStreamProvider printStreamProvider;
    private PrintStream err;
    private PrintStream out;
    private ShredHelper shredHelper;
    private FileResolveHelper fileResolveHelper;
    private I18N strings;
    private Settings settings;

    @Before
    public void setup() {
        strings = new I18N("en", "UK");
        err = mock(PrintStream.class);
        out = mock(PrintStream.class);
        printStreamProvider = mock(PrintStreamProvider.class);
        when(printStreamProvider.err()).thenReturn(err);
        when(printStreamProvider.out()).thenReturn(out);
        osDao = mock(OSDao.class);
        shredHelper = mock(ShredHelper.class);
        fileResolveHelper = mock(FileResolveHelper.class);
        settings = mock(Settings.class);
        clean = new Clean(strings, printStreamProvider, shredHelper, settings);
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
