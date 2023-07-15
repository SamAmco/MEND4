package co.samco.mend4.desktop.commands

import co.samco.mend4.desktop.dao.SettingsDao
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import commands.TestBase
import org.junit.Before
import org.junit.Test

class GclTest : TestBase() {

    private lateinit var uut: Gcl

    @Before
    override fun setup() {
        super.setup()
        uut = Gcl(
            log = log,
            strings = strings,
            settings = settings
        )
    }


    @Test
    fun testGcl() {
        whenever(settings.getValue(eq(SettingsDao.CURRENT_LOG))).thenReturn("test")

        uut.executeCommand(emptyList())

        verify(out).println(eq("test"))
    }
}