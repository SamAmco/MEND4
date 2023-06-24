package commands

import co.samco.mend4.core.Settings
import co.samco.mend4.desktop.commands.SetProperty
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException

class SetPropertyTest : TestBase() {

    private lateinit var setter: SetProperty

    @Before
    override fun setup() {
        super.setup()
        strings = mock()
        setter = SetProperty(log, strings, settings, settingsHelper)
    }

    @Test
    @Throws(IOException::class)
    fun setProperty() {
        val setting = Settings.Name.values()[0]
        val value = "value"
        whenever(settingsHelper.settingExists(anyString())).thenReturn(true)
        setter.execute(listOf(setting.toString(), value))
        verify(settings).setValue(eq(setting), eq(value))
    }

    @Test
    fun setUnknownProperty() {
        val property = "anunknownproperty"
        whenever(settingsHelper.settingExists(anyString())).thenReturn(false)
        setter.execute(listOf(property, "value"))
        verify(strings).getf(eq("SetProperty.notRecognised"), eq(property))
    }

    @Test
    fun tooManyArgs() {
        setter.execute(listOf("anunknownproperty", "value", "extraarg"))
        verify(strings).getf(eq("General.invalidArgNum"), eq(SetProperty.COMMAND_NAME))
    }

    @Test
    fun tooFewArgs() {
        setter.execute(listOf("anunknownproperty"))
        verify(strings).getf(eq("General.invalidArgNum"), eq(SetProperty.COMMAND_NAME))
    }
}