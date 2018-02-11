package commands;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.impl.SettingsImpl;
import co.samco.mend4.desktop.commands.SetProperty;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import helper.FakeLazy;
import org.junit.Before;
import org.junit.Test;

import javax.xml.transform.TransformerException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Map;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SetPropertyTest {
    private SetProperty setter;
    private Settings settings;
    private PrintStreamProvider log;
    private I18N strings;
    private PrintStream err;
    private PrintStream out;

    @Before
    public void setup() {
        settings = mock(Settings.class);
        err = mock(PrintStream.class);
        out = mock(PrintStream.class);
        log = mock(PrintStreamProvider.class);
        when(log.err()).thenReturn(err);
        when(log.out()).thenReturn(out);
        strings = mock(I18N.class);
        setter = new SetProperty(log, strings, new FakeLazy<>(settings));
    }

    @Test
    public void setProperty() throws SettingsImpl.InvalidSettingNameException, TransformerException, SettingsImpl.CorruptSettingsException {
        Map.Entry<Integer, String> setting = Config.SETTINGS_NAMES_MAP.entrySet().iterator().next();
        String value = "value";
        setter.execute(Arrays.asList(setting.getValue(), value));
        verify(settings).setValue(Config.Settings.values()[setting.getKey()], value);
    }

    @Test
    public void setUnknownProperty() {
        String property = "anunknownproperty";
        setter.execute(Arrays.asList(property, "value"));
        verify(strings).getf(eq("SetProperty.notRecognised"), eq(property));
    }

    @Test
    public void tooManyArgs() {
        setter.execute(Arrays.asList("anunknownproperty", "value", "extraarg"));
        verify(strings).get(eq("SetProperty.invalidArgNum"));
    }

    @Test
    public void tooFewArgs() {
        setter.execute(Arrays.asList("anunknownproperty"));
        verify(strings).get(eq("SetProperty.invalidArgNum"));
    }
}
