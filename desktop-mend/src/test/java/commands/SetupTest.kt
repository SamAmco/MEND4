package commands

import co.samco.mend4.core.AppProperties
import co.samco.mend4.core.Settings
import co.samco.mend4.desktop.commands.Setup
import co.samco.mend4.desktop.helper.KeyHelper
import org.junit.Assert
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
import java.io.FileNotFoundException
import java.io.IOException
import java.security.KeyPair
import java.util.Arrays

class SetupTest : TestBase() {

    private lateinit var setup: Setup
    private lateinit var errCaptor: KArgumentCaptor<String>
    private val mendDirPath = "settingspath"
    private val settingsFilePath = "$mendDirPath/settings.file"

    @Before
    override fun setup() {
        super.setup()
        errCaptor = argumentCaptor()
        whenever(fileResolveHelper.settingsFile).thenReturn(settingsFilePath)
        whenever(fileResolveHelper.getMendDirPath()).thenReturn(mendDirPath)
        setup = Setup(log, strings, osDao, keyHelper, cryptoProvider, fileResolveHelper, settings)
    }

    @Test
    fun alreadySetUp() {
        whenever(osDao.fileExists(any(File::class.java))).thenReturn(true)
        setup.execute(emptyList())
        verify(err).println(errCaptor.capture())
        Assert.assertEquals(
            strings.getf("SetupMend.alreadySetup", settingsFilePath, Setup.FORCE_FLAG),
            errCaptor.value
        )
    }

    @Test
    fun wrongArgNum3() {
        setup.execute(Arrays.asList("a", "b", "c"))
        verify(err).println(errCaptor.capture())
        Assert.assertEquals(
            strings.getf("General.invalidArgNum", Setup.COMMAND_NAME),
            errCaptor.value
        )
    }

    @Test
    fun wrongArgNum1() {
        setup.execute(Arrays.asList("a"))
        verify(err).println(errCaptor.capture())
        Assert.assertEquals(
            strings.getf("General.invalidArgNum", Setup.COMMAND_NAME),
            errCaptor.value
        )
    }

    @Test
    fun passwordsDontMatch() {
        val passOne = "passOne"
        val passTwo = "passTwo"
        var count = 0
        whenever(osDao.readPassword(anyString())).thenAnswer {
            return@thenAnswer if (count++ == 0) {
                passOne.toCharArray()
            } else {
                passTwo.toCharArray()
            }
        }
        whenever(cryptoProvider.storeEncryptedKeys(any(), any()))
        setup.execute(emptyList())
        verify(err, times(2)).println(errCaptor.capture())
        verify(cryptoProvider, never()).storeEncryptedKeys(any(), any())
        Assert.assertEquals(strings["SetupMend.passwordMismatch"], errCaptor.allValues[0])
        Assert.assertEquals(strings["SetupMend.complete"], errCaptor.allValues[1])
    }

    @Test
    fun setupFromKeyFiles() {
        val keyInfo: EncodedKeyInfo = doSetupFromKeyFiles()
        verifySettingsSetup(keyInfo)
        Assert.assertEquals(strings["SetupMend.complete"], errCaptor.value)
    }

    @Test
    fun setupFromKeyFilesNull() {
        val exception = "exception"
        whenever(osDao.readPassword(anyString()))
            .thenReturn("password".toCharArray())
        whenever(fileResolveHelper.resolveFile(anyString())).thenThrow(
            FileNotFoundException(exception)
        )
        setup.execute(Arrays.asList("x", "y"))
        verify(err).println(errCaptor.capture())
        Assert.assertEquals(exception, errCaptor.value)
    }

    @Test
    fun setupSettingsThrowsException() {
        val exception = "exception"
        whenever(osDao.readPassword(anyString()))
            .thenReturn("password".toCharArray())
        val keyInfo = EncodedKeyInfo("a", "b", 0)
        whenever(
            cryptoProvider.storeEncryptedKeys(
                any(
                    CharArray::class.java
                ), any(KeyPair::class.java)
            )
        ).thenReturn(keyInfo)
        doThrow(IOException(exception)).whenever(settings).setValue(
            any(
                Settings.Name::class.java
            ), anyString()
        )
        setup.execute(Arrays.asList("x", "y"))
        verify(err).println(errCaptor.capture())
        Assert.assertEquals(exception, errCaptor.value)
    }

    @Test
    fun doesntOverrideSettingsAlreadySet() {
        whenever(settings.valueSet(eq(Settings.Name.PREFERREDAES)))
            .thenReturn(true)
        doSetupFromKeyFiles()
        verify<Settings>(settings, never()).setValue(
            eq<Settings.Name>(
                Settings.Name.PREFERREDAES
            ), anyString()
        )
        Assert.assertEquals(strings["SetupMend.complete"], errCaptor.value)
    }

    fun doSetupFromKeyFiles(): EncodedKeyInfo {
        whenever(osDao.readPassword(anyString()))
            .thenReturn("password".toCharArray())
        val keyInfo = EncodedKeyInfo("a", "b", 0)
        whenever(
            cryptoProvider.storeEncryptedKeys(
                any(
                    CharArray::class.java
                ), any(KeyPair::class.java)
            )
        ).thenReturn(keyInfo)
        whenever(fileResolveHelper.resolveFile(anyString()))
            .thenReturn(File(""))
        setup.execute(Arrays.asList("x", "y"))
        verify<KeyHelper>(keyHelper).readKeyPairFromFiles(
            any(File::class.java), any(
                File::class.java
            )
        )
        verify(err).println(errCaptor.capture())
        return keyInfo
    }

    private fun verifySettingsSetup(keyInfo: EncodedKeyInfo) {
        verify<Settings>(settings).setValue(
            eq<Settings.Name>(Settings.Name.PREFERREDAES),
            eq<String>(AppProperties.PREFERRED_SYMMETRIC_TRANSFORM)
        )
        verify<Settings>(settings).setValue(
            eq<Settings.Name>(Settings.Name.PREFERREDRSA),
            eq<String>(AppProperties.PREFERRED_ASYMMETRIC_TRANSFORM)
        )
        verify<Settings>(settings).setValue(
            eq<Settings.Name>(Settings.Name.AESKEYSIZE),
            eq<String>(
                Integer.toString(AppProperties.PREFFERED_SYMMETRIC_KEY_SIZE)
            )
        )
        verify(settings).setValue(
            eq(Settings.Name.ASYMMETRIC_CIPHER_NAME),
            eq(keyInfo.getKeySize().toString())
        )
        verify(settings).setValue(
            eq(Settings.Name.ENCRYPTED_PRIVATE_KEY),
            eq(keyInfo.getEncryptedPrivateKey())
        )
        verify(settings).setValue(eq(Settings.Name.PUBLIC_KEY), eq(keyInfo.getPublicKey()))
    }
}