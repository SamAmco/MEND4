package commands

import co.samco.mend4.core.Settings
import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.desktop.commands.Command
import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.dao.OSDao
import co.samco.mend4.desktop.exception.SettingRequiredException
import co.samco.mend4.desktop.helper.CryptoHelper
import co.samco.mend4.desktop.helper.FileResolveHelper
import co.samco.mend4.desktop.helper.InputHelper
import co.samco.mend4.desktop.helper.KeyHelper
import co.samco.mend4.desktop.helper.MergeHelper
import co.samco.mend4.desktop.helper.SettingsHelper
import co.samco.mend4.desktop.helper.ShredHelper
import co.samco.mend4.desktop.helper.VersionHelper
import co.samco.mend4.desktop.output.PrintStreamProvider
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.contrib.java.lang.system.ExpectedSystemExit
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.io.IOException
import java.io.PrintStream

open class TestBase {
    @JvmField
    @Rule
    val exit: ExpectedSystemExit = ExpectedSystemExit.none()

    protected lateinit var cryptoHelper: CryptoHelper

    protected lateinit var cryptoProvider: CryptoProvider

    protected lateinit var fileResolveHelper: FileResolveHelper

    protected lateinit var inputHelper: InputHelper

    protected lateinit var keyHelper: KeyHelper

    protected lateinit var mergeHelper: MergeHelper

    protected lateinit var settingsHelper: SettingsHelper

    protected lateinit var shredHelper: ShredHelper

    protected lateinit var versionHelper: VersionHelper

    protected lateinit var strings: I18N

    protected lateinit var log: PrintStreamProvider

    protected lateinit var out: PrintStream

    protected lateinit var err: PrintStream

    protected lateinit var settings: Settings

    protected lateinit var osDao: OSDao

    @Before
    open fun setup() {
        strings = I18N("en", "UK")
        err = Mockito.mock(PrintStream::class.java)
        out = Mockito.mock(PrintStream::class.java)
        log = Mockito.mock(PrintStreamProvider::class.java)
        whenever(log.err()).thenReturn(err)
        whenever(log.out()).thenReturn(out)
        cryptoHelper = Mockito.mock(CryptoHelper::class.java)
        cryptoProvider = Mockito.mock(CryptoProvider::class.java)
        fileResolveHelper = Mockito.mock(FileResolveHelper::class.java)
        inputHelper = Mockito.mock(InputHelper::class.java)
        keyHelper = Mockito.mock(KeyHelper::class.java)
        mergeHelper = Mockito.mock(MergeHelper::class.java)
        settingsHelper = Mockito.mock(SettingsHelper::class.java)
        shredHelper = Mockito.mock(ShredHelper::class.java)
        versionHelper = Mockito.mock(VersionHelper::class.java)
        settings = Mockito.mock(Settings::class.java)
        osDao = Mockito.mock(OSDao::class.java)
    }

    @Throws(IOException::class, SettingRequiredException::class)
    protected fun testCommandWithNoSettingsDependencies(command: Command) {
        val message = "message"
        doThrow(SettingRequiredException(message))
            .whenever(settingsHelper)
            .assertRequiredSettingsExist(any(), any())

        command.executeCommand(ArrayList())
        Mockito.verify(err).println(eq(message))
        Assert.assertNotEquals(0, command.executionResult.toLong())
    }
}