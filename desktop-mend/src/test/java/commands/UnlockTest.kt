package commands

import co.samco.mend4.core.Settings
import co.samco.mend4.core.crypto.UnlockResult
import co.samco.mend4.desktop.commands.Unlock
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import com.nhaarman.mockitokotlin2.KArgumentCaptor
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.apache.commons.codec.binary.Base64
import org.junit.Assert.assertEquals
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.PrivateKey

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
        whenever(cryptoProvider.unlock(any())).thenReturn(UnlockResult.Failure)

        unlock.execute(emptyList())

        verify(err).println(eq(strings["Unlock.incorrectPassword"]))
        verify(osDao, never()).exists(any())
        verify(shredHelper, never()).tryShredFile(any())
        verify(osDao, never()).fileOutputSteam(any(), any())
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

    private fun privateKeyForBytes(byteArray: ByteArray): PrivateKey {
        return object : PrivateKey {
            override fun getAlgorithm(): String = "algorithm"

            override fun getFormat(): String = "format"

            override fun getEncoded(): ByteArray = byteArray
        }
    }

    private fun correctPasswordTest() {
        val password = "password".toCharArray()
        whenever(osDao.readPassword(anyString())).thenReturn(password)
        whenever(cryptoProvider.unlock(eq(password)))
            .thenReturn(UnlockResult.Success(privateKeyForBytes("privateKey".toByteArray())))
        whenever(settings.getValue(eq(Settings.Name.PUBLIC_KEY)))
            .thenReturn(Base64.encodeBase64URLSafeString("publicKey".toByteArray()))

        val privateKeyBytes = ByteArrayOutputStream()
        val publicKeyBytes = ByteArrayOutputStream()
        whenever(osDao.fileOutputSteam(eq(privateKeyFile), eq(false)))
            .thenReturn(privateKeyBytes)
        whenever(osDao.fileOutputSteam(eq(publicKeyFile), eq(false)))
            .thenReturn(publicKeyBytes)

        unlock.execute(emptyList())
        verify(osDao, times(1)).fileOutputSteam(eq(privateKeyFile), eq(false))
        verify(osDao, times(1)).fileOutputSteam(eq(publicKeyFile), eq(false))
        assertEquals("privateKey", String(privateKeyBytes.toByteArray()))
        assertEquals("publicKey", String(publicKeyBytes.toByteArray()))
    }
}