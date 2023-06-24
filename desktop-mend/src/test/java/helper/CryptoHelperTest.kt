package helper

import co.samco.mend4.core.AppProperties
import co.samco.mend4.core.Settings
import co.samco.mend4.core.util.LogUtils
import co.samco.mend4.desktop.exception.MendLockedException
import co.samco.mend4.desktop.helper.CryptoHelper
import commands.TestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.security.PrivateKey
import java.util.regex.Pattern

class CryptoHelperTest : TestBase() {
    private val encDir = File.separatorChar.toString() + "output" + File.separatorChar + "directory"
    private var privateKey: PrivateKey = mock()

    @Before
    override fun setup() {
        super.setup()
        cryptoHelper = CryptoHelper(
            strings = strings,
            log = log,
            fileResolveHelper = fileResolveHelper,
            settings = settings,
            cryptoProvider = cryptoProvider,
            keyHelper = keyHelper,
            versionHelper = versionHelper,
            osDao = osDao
        )
    }

    @Test
    fun testEncInputOutputFilesCorrect() {
        val inputFileName = "/input.txt"
        val inputFile = File(inputFileName)
        whenever(settings.getValue(Settings.Name.ENCDIR)).thenReturn(encDir)
        cryptoHelper.encryptFile(inputFile, null)
        val fileCaptor1 = argumentCaptor<File>()
        verify(osDao).fileInputStream(fileCaptor1.capture())
        assertEquals(inputFileName, fileCaptor1.firstValue.absolutePath)
        val fileCaptor2 = argumentCaptor<File>()
        verify(osDao).fileOutputSteam(fileCaptor2.capture())
        val pattern =
            Pattern.compile(encDir + File.separatorChar + "\\d{17}." + AppProperties.ENC_FILE_EXTENSION)
        val matcher = pattern.matcher(fileCaptor2.firstValue.absolutePath)
        assertTrue(matcher.matches())
        verify(cryptoProvider).encryptEncStream(any(), any(), eq("txt"))
    }

    @Test
    fun testEncInputFileNoExtension() {
        val inputFileName = "/input"
        val inputFile = File(inputFileName)
        whenever(settings.getValue(Settings.Name.ENCDIR)).thenReturn(encDir)
        cryptoHelper.encryptFile(inputFile, null)
        val fileCaptor = argumentCaptor<File>()
        verify(osDao).fileInputStream(fileCaptor.capture())
        assertEquals(inputFileName, fileCaptor.firstValue.absolutePath)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testDontOverwriteEncFile() {
        val inputFile = File("input")
        whenever(settings.getValue(Settings.Name.ENCDIR)).thenReturn(encDir)
        doThrow(IllegalArgumentException())
            .whenever(fileResolveHelper)
            .assertFileDoesNotExist(any())
        cryptoHelper.encryptFile(inputFile, null)
    }

    @Test
    fun testEncEmptyLog() {
        cryptoHelper.encryptTextToLog(CharArray(0), true)
        verify(fileResolveHelper, never()).currentLogFile
        verify(cryptoProvider, never()).encryptLogStream(any(), any())
    }

    @Test
    fun testLogEncrypted() {
        val message = "hi"
        val logFile = File("/currentLogFile." + AppProperties.LOG_FILE_EXTENSION)
        whenever(fileResolveHelper.currentLogFile).thenReturn(logFile)
        cryptoHelper.encryptTextToLog(message.toCharArray(), true)
        verify(osDao).createNewFile(any())
        verify(osDao).fileOutputSteam(eq(logFile), eq(true))
        verify(cryptoProvider).encryptLogStream(eq(message), any())
    }

    @Test
    fun testHeaderAddedToLog() {
        val version = "ver"
        val message = "hi"
        val messageWithHeader: String =
            LogUtils.addHeaderToLogText(message, strings["Platform.header"], version, "\n")
        val logFile = File("/currentLogFile." + AppProperties.LOG_FILE_EXTENSION)
        whenever(versionHelper.version).thenReturn(version)
        whenever(fileResolveHelper.currentLogFile).thenReturn(logFile)
        cryptoHelper.encryptTextToLog(message.toCharArray(), false)
        verify(cryptoProvider).encryptLogStream(eq(messageWithHeader), any())
    }

    @Test
    fun testDecryptLog() {
        val logFile = File("/logfile.log")
        whenever(keyHelper.privateKey).thenReturn(privateKey)
        cryptoHelper.decryptLog(logFile)
        verify(osDao).fileInputStream(eq(logFile))
        verify(cryptoProvider).decryptLogStream(any(), any(), eq(out))
    }

    @Test(expected = MendLockedException::class)
    fun testDecryptLogMendLocked() {
        cryptoHelper.decryptLog(File(""))
    }

    @Test
    fun testDecryptFile() {
        val encFileName = "encFile"
        val fileExtension = "txt"
        val encFile =
            File(File.separator + "path" + File.separator + encFileName + "." + AppProperties.ENC_FILE_EXTENSION)
        val fileCaptor = argumentCaptor<File>()
        val decDir = File.separator + "decDir"
        whenever(settings.getValue(Settings.Name.DECDIR)).thenReturn(decDir)
        whenever(cryptoProvider.decryptEncStream(any(), any(), any())).thenReturn(fileExtension)
        whenever(keyHelper.privateKey).thenReturn(privateKey)

        cryptoHelper.decryptFile(encFile, false)

        verify(fileResolveHelper).assertDirWritable(decDir)
        verify(fileResolveHelper, times(2))
            .assertFileDoesNotExist(fileCaptor.capture())
        assertEquals(
            fileCaptor.allValues[0].absolutePath,
            decDir + File.separatorChar + encFileName
        )
        assertEquals(
            fileCaptor.allValues[1].absolutePath,
            decDir + File.separatorChar + encFileName + "." + fileExtension
        )
        verify(cryptoProvider).decryptEncStream(any(), any(), any())
        verify(osDao).renameFile(
            any(),
            eq(File(decDir + File.separator + encFileName + "." + fileExtension))
        )
        verify(osDao).desktopOpenFile(any())
    }

    @Test(expected = MendLockedException::class)
    fun testDecryptFileMendLocked() {
        cryptoHelper.decryptFile(File(""), false)
    }
}