package commands

import co.samco.mend4.desktop.commands.Lock
import co.samco.mend4.desktop.dao.SettingsDao
import co.samco.mend4.desktop.helper.impl.ShredHelperImpl
import com.nhaarman.mockitokotlin2.KArgumentCaptor
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import testutils.TestUtils
import java.io.File

class LockTest : TestBase() {
    private lateinit var lock: Lock

    private val privateKeyPath = "test-privpath"
    private val publicKeyPath = "test-pubpath"

    private val privateKeyFile = File(privateKeyPath)
    private val publicKeyFile = File(publicKeyPath)

    @Before
    override fun setup() {
        super.setup()
        whenever(fileResolveHelper.privateKeyFile).thenReturn(privateKeyFile)
        whenever(fileResolveHelper.publicKeyFile).thenReturn(publicKeyFile)

        val shredHelper = ShredHelperImpl(
            strings = strings,
            settings = settings,
            log = log,
            fileResolveHelper = fileResolveHelper,
            osDao = osDao,
        )

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

    private fun testLock() {
        whenever(settings.getValue(SettingsDao.SHRED_COMMAND)).thenReturn("")
        val process = mock<Process>()
        whenever(process.inputStream).thenReturn(TestUtils.emptyInputStream)
        whenever(process.errorStream).thenReturn(TestUtils.emptyInputStream)
        whenever(osDao.exec(any())).thenReturn(process)
        lock.execute(emptyList())
    }

    private fun assertCleaning(stdErr: KArgumentCaptor<String>, startInd: Int) {
        Assert.assertEquals(
            strings.getf("Shred.cleaning", privateKeyFile.absolutePath),
            stdErr.allValues[startInd]
        )
        Assert.assertEquals(
            strings.getf("Shred.cleaning", publicKeyFile.absolutePath),
            stdErr.allValues[startInd + 1]
        )
    }

    @Test
    fun testKeyNotFound() {
        whenever(osDao.exists(any())).thenReturn(false)
        val stdErr = argumentCaptor<String>()
        testLock()
        verify(err, times(4)).println(stdErr.capture())
        println(stdErr.allValues)
        Assert.assertEquals(strings["Lock.notUnlocked"], stdErr.allValues[0])
        assertCleaning(stdErr, 1)
        Assert.assertEquals(strings["Lock.locked"], stdErr.allValues[3])
    }

    @Test
    fun testKeyFoundAndLockFailed() {
        whenever(osDao.exists(any())).thenReturn(true)
        val stdErr = argumentCaptor<String>()
        testLock()
        verify(err, times(3)).println(stdErr.capture())
        assertCleaning(stdErr, 0)
        Assert.assertEquals(strings["Lock.lockFailed"], stdErr.allValues[2])
    }

    @Test
    fun testKeyFoundAndLockPassed() {
        var count = 0
        whenever(osDao.exists(any())).thenAnswer { count++ <= 0 }
        val stdErr = argumentCaptor<String>()
        testLock()
        verify(err, times(3)).println(stdErr.capture())
        assertCleaning(stdErr, 0)
        Assert.assertEquals(strings["Lock.locked"], stdErr.allValues[2])
    }
}