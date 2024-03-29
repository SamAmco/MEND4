package commands

import co.samco.mend4.desktop.commands.Version
import org.junit.Before
import org.junit.Test
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever

class VersionTest : TestBase() {
    private lateinit var version: Version

    @Before
    override fun setup() {
        super.setup()
        version = Version(strings, log, versionHelper)
    }

    @Test
    fun testVersion() {
        val versionName = "test"
        whenever(versionHelper.version).thenReturn(versionName)
        version.execute(emptyList())
        verify(err).println(strings.getf("Version.desktopVersion", versionName))
    }
}