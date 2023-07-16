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
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.KeyPair

class ChangePasswordTest : TestBase() {

    private lateinit var uut: ChangePassword
    private val privateKeyFile = File("privateKeyFile")
    private val publicKeyFile = File("publicKeyFile")

    @Before
    override fun setup() {
        super.setup()
        whenever(fileResolveHelper.privateKeyFile).thenReturn(privateKeyFile)
        whenever(fileResolveHelper.publicKeyFile).thenReturn(publicKeyFile)

        uut = ChangePassword(
            log = log,
            strings = strings,
            cryptoProvider = cryptoProvider,
            fileResolveHelper = fileResolveHelper,
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
    fun testFailsIfNotUnlocked() {
        assumeMendIsSetup()
        whenever(osDao.exists(eq(privateKeyFile))).thenReturn(false)

        uut.execute(emptyList())

        verify(err).println(strings.getf("ChangePassword.mendLocked", Unlock.COMMAND_NAME))
        verify(settings, never()).setValue(any(), any())
        verify(cryptoProvider, never()).storeEncryptedKeys(any(), any())
    }

    @Test
    fun testSuccessfullyChangePassword() {
        assumeMendIsSetup()
        whenever(osDao.exists(eq(privateKeyFile))).thenReturn(true)
        whenever(osDao.exists(eq(publicKeyFile))).thenReturn(true)
        val privateKeyBytes = "privateKeyBytes".toByteArray()
        val publicKeyBytes = "publicKeyBytes".toByteArray()
        whenever(osDao.readAllBytes(eq(privateKeyFile))).thenReturn(privateKeyBytes)
        whenever(osDao.readAllBytes(eq(publicKeyFile))).thenReturn(publicKeyBytes)
        val password = "somePassword".toCharArray()
        whenever(osDao.readPassword(any())).thenReturn(password)

        val keyPair = KeyPair(null, null)
        whenever(cryptoProvider.getKeyPairFromBytes(eq(privateKeyBytes), eq(publicKeyBytes)))
            .thenReturn(keyPair)
        var i = 0
        whenever(osDao.readLine()).thenAnswer { (i++).toString() }

        val outCaptor = argumentCaptor<String>()

        uut.execute(emptyList())

        verify(out, atLeastOnce()).println(outCaptor.capture())
        assertEquals(strings["ChangePassword.success"], outCaptor.lastValue)
        verify(cryptoProvider).storeEncryptedKeys(eq(password), eq(keyPair))
        verify(settings).setValue(eq(Settings.Name.PW_KEY_FACTORY_ITERATIONS), eq("0"))
        verify(settings).setValue(eq(Settings.Name.PW_KEY_FACTORY_PARALLELISM), eq("1"))
        verify(settings).setValue(eq(Settings.Name.PW_KEY_FACTORY_MEMORY_KB), eq("2"))
    }

    @Test
    fun shouldNotStoreAnythingIfAnyExceptionIsThrown() {
        assumeMendIsSetup()
        whenever(osDao.exists(eq(privateKeyFile))).thenReturn(true)
        whenever(osDao.exists(eq(publicKeyFile))).thenReturn(true)
        val privateKeyBytes = "privateKeyBytes".toByteArray()
        val publicKeyBytes = "publicKeyBytes".toByteArray()
        whenever(osDao.readAllBytes(eq(privateKeyFile))).thenReturn(privateKeyBytes)
        whenever(osDao.readAllBytes(eq(publicKeyFile))).thenReturn(publicKeyBytes)
        val password = "somePassword".toCharArray()
        whenever(osDao.readPassword(any())).thenReturn(password)

        val keyPair = KeyPair(null, null)
        whenever(cryptoProvider.getKeyPairFromBytes(eq(privateKeyBytes), eq(publicKeyBytes)))
            .thenReturn(keyPair)
        var i = 0
        whenever(osDao.readLine()).thenAnswer {
            if (i == 1) throw Exception("some exception")
            else (i++).toString()
        }

        uut.execute(emptyList())

        verify(err).println("some exception")
        verify(cryptoProvider, never()).storeEncryptedKeys(any(), any())
        verify(settings, never()).setValue(any(), any())
    }

    @Test
    fun undoesAnyChangesIfStoreKeysFails() {
        assumeMendIsSetup()
        whenever(osDao.exists(eq(privateKeyFile))).thenReturn(true)
        whenever(osDao.exists(eq(publicKeyFile))).thenReturn(true)
        val privateKeyBytes = "privateKeyBytes".toByteArray()
        val publicKeyBytes = "publicKeyBytes".toByteArray()
        whenever(osDao.readAllBytes(eq(privateKeyFile))).thenReturn(privateKeyBytes)
        whenever(osDao.readAllBytes(eq(publicKeyFile))).thenReturn(publicKeyBytes)
        val password = "somePassword".toCharArray()
        whenever(osDao.readPassword(any())).thenReturn(password)

        val keyPair = KeyPair(null, null)
        whenever(cryptoProvider.getKeyPairFromBytes(eq(privateKeyBytes), eq(publicKeyBytes)))
            .thenReturn(keyPair)
        var i = 0
        whenever(osDao.readLine()).thenAnswer { (i++).toString() }

        whenever(cryptoProvider.storeEncryptedKeys(any(), any())).thenAnswer {
            throw Exception("key storing exception")
        }

        val settingNameCaptor = argumentCaptor<Settings.Name>()
        val settingValueCaptor = argumentCaptor<String>()

        uut.execute(emptyList())

        verify(err).println("key storing exception")
        verify(cryptoProvider).storeEncryptedKeys(eq(password), eq(keyPair))
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

}