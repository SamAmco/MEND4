package commands

import co.samco.mend4.desktop.commands.Unlock
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

class UnlockTest : TestBase() {
    private lateinit var unlock: Unlock
    private lateinit var errCaptor: KArgumentCaptor<String>
    private val privateKeyFile = File("privateKeyPath-test")
    private val publicKeyFile = File("publicKeyPath-test")

    @Before
    override fun setup() {
        super.setup()
        errCaptor = argumentCaptor()
        whenever(fileResolveHelper.privateKeyFile).thenReturn(privateKeyFile)
        whenever(fileResolveHelper.publicKeyFile).thenReturn(publicKeyFile)

        unlock = Unlock(
            strings = strings,
            settings = settings,
            settingsHelper = settingsHelper,
            log = log,
            cryptoProvider = cryptoProvider,
            shredHelper = shredHelper,
            fileResolveHelper = fileResolveHelper,
            osDao = osDao,
        )
    }

    @Test
    fun testCommandWithSettingsDependencies() {
        super.testCommandWithNoSettingsDependencies(unlock)
    }

    @Test
    fun wrongPassword() {
        whenever(osDao.readPassword(anyString())).thenReturn(CharArray(0))
        whenever(cryptoProvider.checkPassword(any())).thenReturn(false)

        unlock.execute(emptyList())

        verify(err).println(eq(strings["Unlock.incorrectPassword"]))
        verify(osDao, never()).exists(any())
        verify(shredHelper, never()).tryShredFile(any())
        verify(osDao, never()).fileOutputSteam(any())
    }

    @Test
    fun correctPasswordNoExistingKeys() {
        correctPasswordTest()
        verify(shredHelper, never()).tryShredFile(any())
    }

    @Test
    fun correctPasswordExistingKeys() {
        whenever(osDao.exists(any())).thenReturn(true)
        correctPasswordTest()
        verify(shredHelper, times(2)).tryShredFile(any())
    }

    private fun correctPasswordTest() {
        val password = "password"
        whenever(osDao.readPassword(anyString())).thenReturn(password.toCharArray())
        whenever(cryptoProvider.checkPassword(any())).thenReturn(true)
        unlock.execute(emptyList())
        verify(osDao, times(2)).fileOutputSteam(any())
    }
}