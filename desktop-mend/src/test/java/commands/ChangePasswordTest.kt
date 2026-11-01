package co.samco.mend4.desktop.commands

import co.samco.mend4.core.Settings
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import commands.TestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import co.samco.mend4.core.crypto.UnlockResult
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.argThat
import java.security.PrivateKey
import java.security.PublicKey

class ChangePasswordTest : TestBase() {

    private lateinit var uut: ChangePassword

    @Before
    override fun setup() {
        super.setup()

        uut = ChangePassword(
            log = log,
            strings = strings,
            cryptoProvider = cryptoProvider,
            settings = settings,
            osDao = osDao
        )
    }

    private fun assumeMendIsSetup() {
        whenever(settings.getValue(Settings.Name.PW_KEY_FACTORY_SALT)).thenReturn("old-salt")
        whenever(settings.getValue(Settings.Name.PW_KEY_FACTORY_ITERATIONS)).thenReturn("4")
        whenever(settings.getValue(Settings.Name.PW_KEY_FACTORY_PARALLELISM)).thenReturn("5")
        whenever(settings.getValue(Settings.Name.PW_KEY_FACTORY_MEMORY_KB)).thenReturn("6")
        whenever(settings.getValue(Settings.Name.PW_PRIVATE_KEY_CIPHER_IV)).thenReturn("old-iv")
        whenever(settings.getValue(Settings.Name.ENCRYPTED_PRIVATE_KEY)).thenReturn("old-privateKey")
        whenever(settings.getValue(Settings.Name.PUBLIC_KEY)).thenReturn("old-publicKey")
    }

    @Test
    fun testMendNotSetup() {
        uut.execute(emptyList())

        verify(err).println(
            eq(
                strings.getf(
                    "ChangePassword.missingSetting",
                    Settings.Name.PW_KEY_FACTORY_SALT.encodedName
                )
            )
        )
        verify(settings, never()).setValue(any(), any())
        verify(cryptoProvider, never()).storeEncryptedKeys(any(), any())
    }

    @Test
    fun testFailsIfAuthenticationFails() {
        assumeMendIsSetup()
        whenever(osDao.readPassword(any())).thenReturn(charArrayOf())
        whenever(cryptoProvider.unlock(any())).thenReturn(UnlockResult.Failure)

        uut.execute(emptyList())

        verify(err).println("Incorrect password")
        verify(settings, never()).setValue(any(), any())
        verify(cryptoProvider, never()).storeEncryptedKeys(any(), any())
    }

    @Test
    fun testSuccessfullyChangePassword() {
        assumeMendIsSetup()
        val password = "somePassword".toCharArray()
        whenever(osDao.readPassword(any())).thenReturn(password)

        val privateKey = mock<PrivateKey>()
        val publicKey = mock<PublicKey>()
        whenever(cryptoProvider.unlock(any())).thenReturn(UnlockResult.Success(privateKey))
        whenever(cryptoProvider.getPublicKey()).thenReturn(publicKey)
        val passwordBuffers = mutableListOf<CharArray>()
        whenever(osDao.readPassword(any())).thenAnswer { "somePassword".toCharArray().also(passwordBuffers::add) }
        whenever(cryptoProvider.storeEncryptedKeys(any(), any())).thenAnswer {
            assertArrayEquals(password, it.arguments[0] as CharArray)
        }
        var i = 0
        whenever(osDao.readLine()).thenAnswer { (i++).toString() }

        val outCaptor = argumentCaptor<String>()

        uut.execute(emptyList())

        verify(out, atLeastOnce()).println(outCaptor.capture())
        assertEquals(strings["ChangePassword.success"], outCaptor.lastValue)
        verify(cryptoProvider).storeEncryptedKeys(any(), argThat { private === privateKey && public === publicKey })
        assertTrue(passwordBuffers.all { buffer -> buffer.all { it == '\u0000' } })
        verify(settings).setValue(eq(Settings.Name.PW_KEY_FACTORY_ITERATIONS), eq("0"))
        verify(settings).setValue(eq(Settings.Name.PW_KEY_FACTORY_PARALLELISM), eq("1"))
        verify(settings).setValue(eq(Settings.Name.PW_KEY_FACTORY_MEMORY_KB), eq("2"))
    }

    @Test
    fun shouldNotStoreAnythingIfAnyExceptionIsThrown() {
        assumeMendIsSetup()

        val privateKey = mock<PrivateKey>()
        val publicKey = mock<PublicKey>()
        whenever(cryptoProvider.unlock(any())).thenReturn(UnlockResult.Success(privateKey))
        whenever(cryptoProvider.getPublicKey()).thenReturn(publicKey)
        val passwordBuffers = mutableListOf<CharArray>()
        whenever(osDao.readPassword(any())).thenAnswer { "somePassword".toCharArray().also(passwordBuffers::add) }
        var i = 0
        whenever(osDao.readLine()).thenAnswer {
            if (i == 1) throw Exception("some exception")
            else (i++).toString()
        }

        uut.execute(emptyList())

        verify(err).println("some exception")
        verify(cryptoProvider, never()).storeEncryptedKeys(any(), any())
        verify(settings, never()).setValue(any(), any())
        assertTrue(passwordBuffers.all { buffer -> buffer.all { it == '\u0000' } })
    }

    @Test
    fun undoesAnyChangesIfStoreKeysFails() {
        assumeMendIsSetup()

        val privateKey = mock<PrivateKey>()
        val publicKey = mock<PublicKey>()
        whenever(cryptoProvider.unlock(any())).thenReturn(UnlockResult.Success(privateKey))
        whenever(cryptoProvider.getPublicKey()).thenReturn(publicKey)
        val passwordBuffers = mutableListOf<CharArray>()
        whenever(osDao.readPassword(any())).thenAnswer { "somePassword".toCharArray().also(passwordBuffers::add) }
        var i = 0
        whenever(osDao.readLine()).thenAnswer { (i++).toString() }

        whenever(cryptoProvider.storeEncryptedKeys(any(), any())).thenAnswer {
            throw Exception("key storing exception")
        }

        val settingNameCaptor = argumentCaptor<Settings.Name>()
        val settingValueCaptor = argumentCaptor<String>()

        uut.execute(emptyList())

        verify(err).println("key storing exception")
        verify(cryptoProvider).storeEncryptedKeys(any(), argThat { private === privateKey && public === publicKey })
        assertTrue(passwordBuffers.all { buffer -> buffer.all { it == '\u0000' } })
        verify(settings, atLeastOnce()).setValue(settingNameCaptor.capture(), settingValueCaptor.capture())

        val keyValuePairs = settingNameCaptor.allValues.zip(settingValueCaptor.allValues)

        //even though we set some/all properties to something else, we set them all back again after
        // the key storing fails
        assertTrue(keyValuePairs.size > 7)

        assertEquals(
            "old-salt",
            keyValuePairs.last { it.first == Settings.Name.PW_KEY_FACTORY_SALT }.second
        )
        assertEquals(
            "4",
            keyValuePairs.last { it.first == Settings.Name.PW_KEY_FACTORY_ITERATIONS }.second
        )
        assertEquals(
            "5",
            keyValuePairs.last { it.first == Settings.Name.PW_KEY_FACTORY_PARALLELISM }.second
        )
        assertEquals(
            "6",
            keyValuePairs.last { it.first == Settings.Name.PW_KEY_FACTORY_MEMORY_KB }.second
        )
        assertEquals(
            "old-iv",
            keyValuePairs.last { it.first == Settings.Name.PW_PRIVATE_KEY_CIPHER_IV }.second
        )
        assertEquals(
            "old-privateKey",
            keyValuePairs.last { it.first == Settings.Name.ENCRYPTED_PRIVATE_KEY }.second
        )
        assertEquals(
            "old-publicKey",
            keyValuePairs.last { it.first == Settings.Name.PUBLIC_KEY }.second
        )
    }

    @Test fun clearsRejectedCandidatesAndConfirmationsBeforeRetrying() {
        assumeMendIsSetup()
        val buffers = listOf("old", "wrong", "mismatch", "new", "new").map(String::toCharArray)
        var index = 0
        whenever(osDao.readPassword(any())).thenAnswer { buffers[index++] }
        whenever(osDao.readLine()).thenReturn("")
        whenever(cryptoProvider.unlock(any())).thenReturn(UnlockResult.Success(mock()))
        whenever(cryptoProvider.getPublicKey()).thenReturn(mock())
        whenever(cryptoProvider.storeEncryptedKeys(any(), any())).thenAnswer {
            assertArrayEquals("new".toCharArray(), it.arguments[0] as CharArray)
            assertTrue(buffers.take(3).all { buffer -> buffer.all { it == '\u0000' } })
            assertTrue(buffers.last().all { it == '\u0000' })
        }
        uut.execute(emptyList())
        assertEquals(5, index)
        assertTrue(buffers.all { buffer -> buffer.all { it == '\u0000' } })
    }

    @Test fun clearsCandidateIfReadingConfirmationFails() {
        assumeMendIsSetup()
        val old = "old".toCharArray()
        val candidate = "new".toCharArray()
        var index = 0
        whenever(osDao.readPassword(any())).thenAnswer {
            when (index++) {
                0 -> old
                1 -> candidate
                else -> error("Terminal closed")
            }
        }
        whenever(cryptoProvider.unlock(any())).thenReturn(UnlockResult.Success(mock()))
        whenever(cryptoProvider.getPublicKey()).thenReturn(mock())
        assertThrows(IllegalStateException::class.java) { uut.execute(emptyList()) }
        assertTrue(old.all { it == '\u0000' })
        assertTrue(candidate.all { it == '\u0000' })
        verify(cryptoProvider, never()).storeEncryptedKeys(any(), any())
    }
}
