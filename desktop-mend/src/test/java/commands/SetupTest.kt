package commands

import co.samco.mend4.core.Settings
import co.samco.mend4.desktop.commands.Setup
import org.junit.Assert
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
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.KeyPair

class SetupTest : TestBase() {

    private lateinit var setup: Setup
    private lateinit var errCaptor: KArgumentCaptor<String>
    private val mendDirPath = File("settingspath")
    private val settingsFilePath = File(mendDirPath, "settings.file")

    @Before
    override fun setup() {
        super.setup()
        errCaptor = argumentCaptor()
        whenever(fileResolveHelper.settingsFile).thenReturn(settingsFilePath)
        whenever(fileResolveHelper.mendDirFile).thenReturn(mendDirPath)

        setup = Setup(
            log = log,
            strings = strings,
            cryptoProvider = cryptoProvider,
            fileResolveHelper = fileResolveHelper,
            settings = settings,
            osDao = osDao
        )
    }

    @Test
    fun alreadySetUp() {
        whenever(osDao.exists(any())).thenReturn(true)
        setup.execute(emptyList())
        verify(err).println(errCaptor.capture())
        Assert.assertEquals(
            strings.getf("SetupMend.alreadySetup", settingsFilePath, Setup.FORCE_FLAG),
            errCaptor.firstValue
        )
    }

    @Test
    fun wrongArgNum3() {
        setup.execute(listOf("a", "b", "c"))
        verify(err).println(errCaptor.capture())
        Assert.assertEquals(
            strings.getf("General.invalidArgNum", Setup.COMMAND_NAME),
            errCaptor.firstValue
        )
    }

    @Test
    fun wrongArgNum1() {
        setup.execute(listOf("a"))
        verify(err).println(errCaptor.capture())
        Assert.assertEquals(
            strings.getf("General.invalidArgNum", Setup.COMMAND_NAME),
            errCaptor.firstValue
        )
    }

    @Test
    fun passwordsDontMatch() {
        val passOne = "passOne"
        val passTwo = "passTwo"
        whenever(osDao.readLine()).thenReturn(" ")
        whenever(osDao.exists(eq(settingsFilePath))).thenReturn(false)
        var count = 0
        whenever(osDao.readPassword(anyString())).thenAnswer {
            return@thenAnswer if (count++ == 0) {
                passOne.toCharArray()
            } else {
                passTwo.toCharArray()
            }
        }

        setup.execute(emptyList())

        verify(err, times(2)).println(errCaptor.capture())
        verify(cryptoProvider, never()).storeEncryptedKeys(any(), any())
        Assert.assertEquals(strings["SetupMend.passwordMismatch"], errCaptor.allValues[0])
        Assert.assertEquals(strings["SetupMend.complete"], errCaptor.allValues[1])
    }

    @Test
    fun setupFromKeyFiles() {
        doSetupFromKeyFiles()
        verifySettingsSetup()
        Assert.assertEquals(strings["SetupMend.complete"], errCaptor.firstValue)
        verify(cryptoProvider, times(1))
            .storeEncryptedKeys(eq("password".toCharArray()), any())
    }

    @Test
    fun setupFromKeyFilesNotFound() {
        val exception = "exception"
        whenever(osDao.readPassword(anyString())).thenReturn("password".toCharArray())
        whenever(osDao.readLine()).thenReturn(" ")
        whenever(fileResolveHelper.resolveFile(anyString()))
            .thenThrow(FileNotFoundException(exception))

        setup.execute(listOf("x", "y"))

        verify(err).println(errCaptor.capture())
        Assert.assertEquals(exception, errCaptor.firstValue)
    }

    @Test
    fun setupSettingsThrowsException() {
        val exception = "exception"
        whenever(osDao.readLine()).thenReturn(" ")
        whenever(osDao.readPassword(anyString()))
            .thenReturn("password".toCharArray())
        var settingsExists = false
        whenever(settings.setValue(any(), anyString())).thenAnswer {
            settingsExists = true
            throw IOException(exception)
        }
        whenever(osDao.exists(eq(settingsFilePath)))
            .thenAnswer { settingsExists }

        setup.execute(listOf("x", "y"))

        verify(err).println(errCaptor.capture())
        verify(osDao).delete(eq(settingsFilePath))
        Assert.assertEquals(exception, errCaptor.firstValue)
    }

    private fun doSetupFromKeyFiles() {
        whenever(osDao.readPassword(anyString())).thenReturn("password".toCharArray())
        whenever(fileResolveHelper.resolveFile(anyString()))
            .thenReturn(File(""))
        whenever(osDao.readLine()).thenReturn(" ")
        whenever(osDao.readAllBytes(any())).thenReturn("".toByteArray())
        whenever(cryptoProvider.getKeyPairFromBytes(any(), any()))
            .thenReturn(KeyPair(null, null))

        setup.execute(listOf("x", "y"))

        verify(cryptoProvider).getKeyPairFromBytes(any(), any())
        verify(osDao, times(2)).readAllBytes(eq(File("")))
        verify(err).println(errCaptor.capture())
    }

    private fun verifySettingsSetup(
        asymmetricCipherName: String = Setup.DEFAULT_ASYMMETRIC_CIPHER,
        asymmetricCipherTransform: String = Setup.DEFAULT_ASYMMETRIC_CIPHER_TRANSFORM,
        asymmetricKeySize: Int = Setup.DEFAULT_KEY_SIZE,
        pwKeyFactoryIterations: Int = Setup.DEFAULT_PW_FACTORY_ITERATIONS,
        pwKeyFactoryParallelism: Int = Setup.DEFAULT_PW_FACTORY_PARALLELISM,
        pwKeyFactoryMemory: Int = Setup.DEFAULT_PW_FACTORY_MEMORY_KB
    ) {
        verify(settings).setValue(
            eq(Settings.Name.ASYMMETRIC_CIPHER_NAME),
            eq(asymmetricCipherName)
        )
        verify(settings).setValue(
            eq(Settings.Name.ASYMMETRIC_CIPHER_TRANSFORM),
            eq(asymmetricCipherTransform)
        )
        verify(settings).setValue(
            eq(Settings.Name.ASYMMETRIC_KEY_SIZE),
            eq(asymmetricKeySize.toString())
        )
        verify(settings).setValue(
            eq(Settings.Name.PW_KEY_FACTORY_ITERATIONS),
            eq(pwKeyFactoryIterations.toString())
        )
        verify(settings).setValue(
            eq(Settings.Name.PW_KEY_FACTORY_PARALLELISM),
            eq(pwKeyFactoryParallelism.toString())
        )
        verify(settings).setValue(
            eq(Settings.Name.PW_KEY_FACTORY_MEMORY_KB),
            eq(pwKeyFactoryMemory.toString())
        )
    }
}