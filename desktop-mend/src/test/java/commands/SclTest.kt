package co.samco.mend4.desktop.commands

import co.samco.mend4.desktop.dao.SettingsDao
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import commands.TestBase
import org.junit.Before
import org.junit.Test

class SclTest : TestBase() {

    private lateinit var uut: Scl

    @Before
    override fun setup() {
        super.setup()
        uut = Scl(
            log = log,
            strings = strings,
            settings = settings
        )
    }

    @Test
    fun testScl() {
        uut.executeCommand(listOf("test"))
        verify(settings).setValue(eq(SettingsDao.CURRENT_LOG), eq("test"))
    }
}