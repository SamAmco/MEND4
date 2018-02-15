package commands;

import co.samco.mend4.core.Settings.CorruptSettingsException;
import co.samco.mend4.core.Settings.InvalidSettingNameException;
import co.samco.mend4.desktop.commands.Clean;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.dao.OSDao;
import co.samco.mend4.desktop.helper.FileResolveHelper;
import co.samco.mend4.desktop.helper.ShredHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
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
        clean = new Clean(strings, printStreamProvider, shredHelper, fileResolveHelper);
    }

    @Test
    public void executesShredCommand() throws InvalidSettingNameException, CorruptSettingsException, IOException {
        String decDir = "dir";
        when(fileResolveHelper.getDecDir()).thenReturn(decDir);
        clean.execute(Collections.emptyList());
        verify(shredHelper).shredFilesInDirectory(eq(decDir));
        verify(err).println(eq(strings.get("Clean.cleanComplete")));
    }

    @Test
    public void printsException() throws InvalidSettingNameException, CorruptSettingsException, IOException {
        String exception = "hi";
        when(fileResolveHelper.getDecDir()).thenThrow(new CorruptSettingsException(exception));
        clean.execute(Collections.emptyList());
        verify(err).println(eq(exception));
    }
}
