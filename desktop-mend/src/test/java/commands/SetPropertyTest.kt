package commands

import co.samco.mend4.desktop.commands.SetProperty
import co.samco.mend4.desktop.dao.SettingsDao
import org.junit.Before
import org.junit.Test
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import java.io.IOException

class SetPropertyTest : TestBase() {

    private lateinit var setter: SetProperty

    @Before
    override fun setup() {
        super.setup()
        strings = mock()
        setter = SetProperty(
            log = log,
            strings = strings,
            settings = settings,
            settingsHelper = settingsHelper
        )
    }

    @Test
    @Throws(IOException::class)
    fun setProperty() {
        val setting = SettingsDao.ALL_SETTINGS[0]
        val value = "value"
        setter.execute(listOf(setting.toString(), value))
        verify(settings).setValue(eq(setting), eq(value))
    }

    @Test
    fun setUnknownProperty() {
        val property = "anunknownproperty"
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