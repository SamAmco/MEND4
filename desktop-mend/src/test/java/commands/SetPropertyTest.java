package commands;

import co.samco.mend4.core.Settings;
import co.samco.mend4.desktop.commands.SetProperty;
import co.samco.mend4.desktop.core.I18N;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class SetPropertyTest extends CommandTest {
    private SetProperty setter;

    @Before
    public void setup() {
        super.setup();
        strings = mock(I18N.class);
        setter = new SetProperty(log, strings, settings, settingsHelper);
    }

    @Test
    public void setProperty() throws IOException {
        Settings.Name setting = Settings.Name.values()[0];
        String value = "value";
        when(settingsHelper.settingExists(anyString())).thenReturn(true);
        setter.execute(Arrays.asList(setting.toString(), value));
        verify(settings).setValue(eq(setting), eq(value));
    }

    @Test
    public void setUnknownProperty() {
        String property = "anunknownproperty";
        when(settingsHelper.settingExists(anyString())).thenReturn(false);
        setter.execute(Arrays.asList(property, "value"));
        verify(strings).getf(eq("SetProperty.notRecognised"), eq(property));
    }

    @Test
    public void tooManyArgs() {
        setter.execute(Arrays.asList("anunknownproperty", "value", "extraarg"));
        verify(strings).getf(eq("General.invalidArgNum"), eq(SetProperty.COMMAND_NAME));
    }

    @Test
    public void tooFewArgs() {
        setter.execute(Arrays.asList("anunknownproperty"));
        verify(strings).getf(eq("General.invalidArgNum"), eq(SetProperty.COMMAND_NAME));
    }
}
