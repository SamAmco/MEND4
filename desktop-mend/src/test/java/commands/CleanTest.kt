package commands

import co.samco.mend4.core.Settings
import co.samco.mend4.core.exception.CorruptSettingsException
import co.samco.mend4.desktop.commands.Clean
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException

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
        whenever(settings.getValue(eq(Settings.Name.DECDIR))).thenReturn(decDir)
        clean.execute(emptyList())
        verify(shredHelper).shredFilesInDirectory(eq(decDir))
        verify(err).println(eq(strings["Clean.cleanComplete"]))
    }

    @Test
    @Throws(CorruptSettingsException::class, IOException::class)
    fun printsException() {
        val exception = "hi"
        whenever(settings.getValue(eq(Settings.Name.DECDIR)))
            .thenThrow(CorruptSettingsException(exception, Settings.Name.DECDIR))
        clean.execute(emptyList())
        verify(err).println(eq(exception))
    }
}