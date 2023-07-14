package commands

import co.samco.mend4.desktop.commands.SetProperty
import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.dao.SettingsDao
import co.samco.mend4.desktop.helper.impl.SettingsHelperImpl
import org.junit.Before
import org.junit.Test
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertTrue
import java.io.IOException

class SetPropertyTest : TestBase() {

    private lateinit var setter: SetProperty

    private val stringsMock: I18N = mock()

    @Before
    override fun setup() {
        super.setup()
        setter = SetProperty(
            log = log,
            strings = stringsMock,
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
        verify(stringsMock).getf(eq("SetProperty.notRecognised"), eq(property))
    }

    @Test
    fun tooManyArgs() {
        setter.execute(listOf("anunknownproperty", "value", "extraarg"))
        verify(stringsMock).getf(eq("General.invalidArgNum"), eq(SetProperty.COMMAND_NAME))
    }

    @Test
    fun tooFewArgs() {
        setter.execute(listOf("anunknownproperty"))
        verify(stringsMock).getf(eq("General.invalidArgNum"), eq(SetProperty.COMMAND_NAME))
    }
}