package commands

import co.samco.mend4.core.Settings
import co.samco.mend4.desktop.commands.Lock
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import testutils.TestUtils
import java.io.File

class LockTest : TestBase() {
    private lateinit var lock: Lock

    private val privateKeyPath = "test-privpath"
    private val publicKeyPath = "test-pubpath"

    @Before
    override fun setup() {
        super.setup()
        whenever(fileResolveHelper.privateKeyFile).thenReturn(File(privateKeyPath))
        whenever(fileResolveHelper.publicKeyFile).thenReturn(File(publicKeyPath))

        lock = Lock(
            log = log,
            settingsHelper = settingsHelper,
            shredHelper = shredHelper,
            strings = strings,
            fileResolveHelper = fileResolveHelper,
            osDao = osDao
        )
    }

    @Test
    fun testCommandWithSettingsDependencies() {
        super.testCommandWithNoSettingsDependencies(lock)
    }

    private fun testLock(): ArgumentCaptor<String> {
        whenever(settings.getValue(Settings.Name.SHREDCOMMAND)).thenReturn("")
        val process = mock<Process>()
        whenever(process.inputStream).thenReturn(TestUtils.getEmptyInputStream())
        val stdErr = ArgumentCaptor.forClass(
            String::class.java
        )
        whenever(osDao.exec(any())).thenReturn(process)
        lock.execute(emptyList())
        return stdErr
    }

    private fun assertCleaning(stdErr: ArgumentCaptor<String>, startInd: Int) {
        Assert.assertEquals(
            strings.getf("Shred.cleaning", privateKeyPath),
            stdErr.allValues[startInd]
        )
        Assert.assertEquals(
            strings.getf("Shred.cleaning", publicKeyPath),
            stdErr.allValues[startInd + 1]
        )
    }

    @Test
    fun testKeyNotFound() {
        whenever(osDao.exists(any())).thenReturn(false)
        val stdErr = testLock()
        verify(err, times(4)).println(stdErr.capture())
        Assert.assertEquals(strings["Lock.notUnlocked"], stdErr.allValues[0])
        assertCleaning(stdErr, 1)
        Assert.assertEquals(strings["Lock.locked"], stdErr.allValues[3])
    }

    @Test
    fun testKeyFoundAndLockFailed() {
        whenever(osDao.exists(any())).thenReturn(true)
        val stdErr = testLock()
        verify(err, times(3)).println(stdErr.capture())
        assertCleaning(stdErr, 0)
        Assert.assertEquals(strings["Lock.lockFailed"], stdErr.allValues[2])
    }

    @Test
    fun testKeyFoundAndLockPassed() {
        var count = 0
        whenever(osDao.exists(any())).thenAnswer { count++ <= 0 }
        val stdErr = testLock()
        verify(err, times(3)).println(stdErr.capture())
        assertCleaning(stdErr, 0)
        Assert.assertEquals(strings["Lock.locked"], stdErr.allValues[2])
    }
}