package commands

import co.samco.mend4.core.AppProperties
import co.samco.mend4.desktop.commands.Decrypt
import co.samco.mend4.desktop.dao.SettingsDao
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import java.io.File
import java.io.FileNotFoundException

class DecryptTest : TestBase() {
    private lateinit var decrypt: Decrypt

    @Before
    override fun setup() {
        super.setup()
        decrypt = Decrypt(
            log = log,
            strings = strings,
            settingsHelper = settingsHelper,
            cryptoHelper = cryptoHelper,
            fileResolveHelper = fileResolveHelper,
            settings = settings,
        )
    }

    @Test
    fun testCommandWithSettingsDependencies() {
        super.testCommandWithNoSettingsDependencies(decrypt)
    }

    @Test
    fun decryptLog() {
        val logFileName = "logFile." + AppProperties.LOG_FILE_EXTENSION
        whenever(keyHelper.privateKey).thenReturn(mock())
        whenever(
            fileResolveHelper.fileExistsAndHasExtension(
                eq(AppProperties.ENC_FILE_EXTENSION),
                any()
            )
        )
            .thenReturn(false)
        whenever(fileResolveHelper.resolveAsLogFilePath(anyString()))
            .thenReturn(File(logFileName))

        decrypt.execute(listOf(logFileName))

        val fileCaptor = argumentCaptor<File>()
        verify(cryptoHelper).decryptLog(fileCaptor.capture())
        assertEquals(logFileName, fileCaptor.firstValue.name)
    }

    @Test
    fun decryptEnc() {
        val encFileName = "sam." + AppProperties.ENC_FILE_EXTENSION
        decryptEnc(encFileName, listOf(encFileName), false)
    }

    @Test
    fun decryptEncSilent() {
        val encFileName = "sam." + AppProperties.ENC_FILE_EXTENSION
        decryptEnc(encFileName, listOf(Decrypt.SILENT_FLAG, encFileName), true)
    }

    @Test
    fun logFileDoesntExist() {
        val fileName = "unknown.extension"
        val exception = "exception test"

        doAnswer { throw FileNotFoundException(exception) }
            .whenever(fileResolveHelper)
            .assertFileExistsAndHasExtension(
                anyString(),
                anyString(),
                //Bit of a hack using an argument captor otherwise mockito doesn't match the call.
                argumentCaptor<File>().capture()
            )

        decrypt.execute(listOf(fileName))

        verify(err).println(eq(exception))
    }

    @Test
    fun encFileDoesntExist() {
        val fileName = "unkown.extension"
        whenever(fileResolveHelper.resolveAsEncFilePath(fileName)).thenReturn(File(fileName))
        whenever(fileResolveHelper.fileExistsAndHasExtension(anyString(), any()))
            .thenReturn(false)
        decrypt.execute(listOf(fileName))
        verify(fileResolveHelper).resolveAsLogFilePath(eq(fileName))
    }

    @Test
    fun noArgs() {
        decrypt.execute(emptyList())
        val errCapture = argumentCaptor<String>()
        verify(err, times(2)).println(errCapture.capture())
        assertEquals(strings["Decrypt.noFile"], errCapture.allValues[0])
        assertEquals(
            strings.getf(
                "Decrypt.usage",
                Decrypt.COMMAND_NAME,
                Decrypt.SILENT_FLAG
            ), errCapture.allValues[1]
        )
    }

    private fun decryptEnc(encFileName: String, args: List<String>, silentFlag: Boolean) {
        whenever(fileResolveHelper.resolveAsEncFilePath(encFileName))
            .thenReturn(File(encFileName))
        whenever(
            fileResolveHelper.fileExistsAndHasExtension(
                eq(AppProperties.ENC_FILE_EXTENSION),
                any()
            )
        ).thenReturn(true)
        whenever(settings.getValue(eq(SettingsDao.DEC_DIR))).thenReturn("decDir")

        decrypt.execute(args)

        val fileCaptor = argumentCaptor<File>()
        verify(fileResolveHelper).fileExistsAndHasExtension(
            eq(AppProperties.ENC_FILE_EXTENSION),
            any()
        )
        verify(cryptoHelper).decryptFile(fileCaptor.capture(), eq("decDir"), eq(silentFlag))
        assertEquals(encFileName, fileCaptor.firstValue.name)
    }
}