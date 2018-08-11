package commands;

import co.samco.mend4.desktop.commands.Version;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class VersionTestBase extends TestBase {

    private Version version;

    @Before
    public void setup() {
        super.setup();
        version = new Version(strings, log, versionHelper);
    }

    @Test
    public void testVersion() {
        String versionName = "test";
        when(versionHelper.getVersion()).thenReturn(versionName);
        version.execute(Collections.emptyList());
        verify(err).println(strings.getf("Version.desktopVersion", versionName));
    }
}
