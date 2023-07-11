package commands

import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.desktop.commands.Command
import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.core.I18NImpl
import co.samco.mend4.desktop.dao.OSDao
import co.samco.mend4.desktop.dao.SettingsDao
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
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.io.IOException
import java.io.PrintStream

open class TestBase {

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

    protected lateinit var settings: SettingsDao

    protected lateinit var osDao: OSDao

    @Before
    open fun setup() {
        err = mock()
        out = mock()
        log = mock()
        whenever(log.err()).thenReturn(err)
        whenever(log.out()).thenReturn(out)
        cryptoProvider = mock()
        settings = mock()
        osDao = mock()
        settingsHelper = mock()
        versionHelper = mock()
        strings = I18NImpl("en", "UK")
        fileResolveHelper = mock()
        inputHelper = mock()
        keyHelper = mock()
        mergeHelper = mock()
        cryptoHelper = mock()
        shredHelper = mock()
    }

    @Throws(IOException::class, SettingRequiredException::class)
    protected fun testCommandWithNoSettingsDependencies(command: Command) {
        val message = "message"
        doThrow(SettingRequiredException(message))
            .whenever(settingsHelper)
            .assertRequiredSettingsExist(any(), any())

        command.executeCommand(ArrayList())
        verify(err).println(eq(message))
        Assert.assertNotEquals(0, command.executionResult.toLong())
    }
}