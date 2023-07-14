package commands

import co.samco.mend4.desktop.commands.Clean
import co.samco.mend4.desktop.dao.SettingsDao
import com.nhaarman.mockitokotlin2.any
import org.junit.Before
import org.junit.Test
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.io.File

class CleanTest : TestBase() {
    private lateinit var clean: Clean

    @Before
    override fun setup() {
        super.setup()
        clean = Clean(
            strings = strings,
            log = log,
            settingsHelper = settingsHelper,
            shredHelper = shredHelper,
            settings = settings
        )
    }

    @Test
    fun testCommandWithSettingsDependencies() {
        super.testCommandWithNoSettingsDependencies(clean)
    }

    @Test
    fun executesShredCommand() {
        val decDir = "dir"
        whenever(settings.getValue(eq(SettingsDao.DEC_DIR))).thenReturn(decDir)
        whenever(settings.getValue(eq(SettingsDao.SHRED_COMMAND))).thenReturn(" ")
        whenever(osDao.listFiles(any())).thenReturn(arrayOf(File("test-file")))

        clean.execute(emptyList())
        verify(shredHelper).shredFilesInDirectory(eq(decDir))
        verify(err).println(eq(strings["Clean.cleanComplete"]))
    }

    @Test
    fun printsException() {
        whenever(settings.getValue(eq(SettingsDao.DEC_DIR))).thenReturn(null)
        clean.execute(emptyList())
        verify(err).println(
            eq(
                strings.getf(
                    "General.propertyNotSet",
                    SettingsDao.DEC_DIR.encodedName,
                    Clean.COMMAND_NAME
                )
            )
        )
    }
}