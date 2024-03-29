package commands

import co.samco.mend4.core.Settings
import co.samco.mend4.desktop.commands.Setup
import co.samco.mend4.desktop.dao.SettingsDao
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import com.nhaarman.mockitokotlin2.KArgumentCaptor
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.io.File
import java.io.IOException
import java.security.KeyPair

class SetupTest : TestBase() {

    private lateinit var setup: Setup
    private lateinit var errCaptor: KArgumentCaptor<String>
    private lateinit var outCaptor: KArgumentCaptor<String>
    private val mendDirPath = File("settingspath")
    private val settingsFilePath = File(mendDirPath, "settings.file")

    @Before
    override fun setup() {
        super.setup()
        errCaptor = argumentCaptor()
        outCaptor = argumentCaptor()
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

        verify(err, times(1)).println(errCaptor.capture())
        verify(cryptoProvider, never()).storeEncryptedKeys(any(), any())
        Assert.assertEquals(strings["SetupMend.passwordMismatch"], errCaptor.allValues[0])
    }

    @Test
    fun setupDefaultParameters() {
        doSetup()
        verifySettingsSetup()
        Assert.assertEquals(strings["SetupMend.complete"], outCaptor.lastValue)
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

        setup.execute(emptyList())

        verify(err).println(errCaptor.capture())
        verify(osDao).delete(eq(settingsFilePath))
        Assert.assertEquals(exception, errCaptor.firstValue)
    }

    private fun doSetup() {
        whenever(osDao.readPassword(anyString())).thenReturn("password".toCharArray())
        whenever(osDao.readLine()).thenReturn(" ")
        whenever(cryptoProvider.generateKeyPair())
            .thenReturn(KeyPair(null, null))

        setup.execute(emptyList())

        verify(cryptoProvider).generateKeyPair()
        verify(cryptoProvider, times(1))
            .storeEncryptedKeys(eq("password".toCharArray()), any())
        verify(out, atLeastOnce()).println(outCaptor.capture())
    }

    private fun verifySettingsSetup(
        asymmetricCipherName: String = Setup.DEFAULT_ASYMMETRIC_CIPHER,
        asymmetricCipherTransform: String = Setup.DEFAULT_ASYMMETRIC_CIPHER_TRANSFORM,
        asymmetricKeySize: Int = Setup.DEFAULT_KEY_SIZE,
        pwKeyFactoryIterations: Int = Setup.DEFAULT_PW_FACTORY_ITERATIONS,
        pwKeyFactoryParallelism: Int = Setup.DEFAULT_PW_FACTORY_PARALLELISM,
        pwKeyFactoryMemory: Int = Setup.DEFAULT_PW_FACTORY_MEMORY_KB,
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

        val logDir = File(mendDirPath, Setup.DEFAULT_LOG_DIR_NAME)
        val encDir = File(mendDirPath, Setup.DEFAULT_ENC_DIR_NAME)
        val decDir = File(mendDirPath, Setup.DEFAULT_DEC_DIR_NAME)
        val shredCommand: String = Setup.DEFAULT_SHRED_COMMAND
        val currentLog: String = Setup.DEFAULT_LOG_FILE_NAME

        verify(osDao).mkdirs(eq(mendDirPath))
        verify(osDao).mkdirs(eq(logDir))
        verify(osDao).mkdirs(eq(encDir))
        verify(osDao).mkdirs(eq(decDir))

        verify(settings).setValue(
            eq(SettingsDao.LOG_DIR),
            eq(logDir.absolutePath)
        )
        verify(settings).setValue(
            eq(SettingsDao.ENC_DIR),
            eq(encDir.absolutePath)
        )
        verify(settings).setValue(
            eq(SettingsDao.DEC_DIR),
            eq(decDir.absolutePath)
        )
        verify(settings).setValue(
            eq(SettingsDao.SHRED_COMMAND),
            eq(shredCommand)
        )
        verify(settings).setValue(
            eq(SettingsDao.CURRENT_LOG),
            eq(currentLog)
        )
    }
}