package commands

import co.samco.mend4.core.AppProperties
import co.samco.mend4.desktop.commands.Decrypt
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
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
            fileResolveHelper = fileResolveHelper
        )
    }

    @Test
    fun testCommandWithSettingsDependencies() {
        super.testCommandWithNoSettingsDependencies(decrypt)
    }

    @Test
    fun decryptLog() {
        val logFileName = "logFile." + AppProperties.LOG_FILE_EXTENSION
        decryptLog(logFileName)
    }

    @Test
    fun decryptLogWithSilentFlag() {
        val logFileName = "logFile." + AppProperties.LOG_FILE_EXTENSION
        decryptLog(logFileName)
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
        val fileName = "unkown.extension"
        val exceptionText = "hi"
        doThrow(FileNotFoundException(exceptionText)).`when`(fileResolveHelper)
            .assertFileExistsAndHasExtension(
                ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.any(
                    File::class.java
                )
            )
        decrypt.execute(listOf(fileName))
        val errCapture = ArgumentCaptor.forClass(
            String::class.java
        )
        verify(err).println(errCapture.capture())
        Assert.assertEquals(exceptionText, errCapture.value)
    }

    @Test
    fun encFileDoesntExist() {
        val fileName = "unkown.extension"
        whenever(fileResolveHelper.resolveAsEncFilePath(fileName)).thenReturn(File(fileName))
        whenever(
            fileResolveHelper.fileExistsAndHasExtension(
                ArgumentMatchers.anyString(), ArgumentMatchers.any(
                    File::class.java
                )
            )
        ).thenReturn(false)
        decrypt.execute(listOf(fileName))
        verify(fileResolveHelper).resolveAsLogFilePath(ArgumentMatchers.eq(fileName))
    }

    @Test
    fun noArgs() {
        decrypt.execute(emptyList())
        val errCapture = ArgumentCaptor.forClass(
            String::class.java
        )
        verify(err, times(2)).println(errCapture.capture())
        Assert.assertEquals(strings["Decrypt.noFile"], errCapture.allValues[0])
        Assert.assertEquals(
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
                ArgumentMatchers.eq(AppProperties.ENC_FILE_EXTENSION), ArgumentMatchers.any(
                    File::class.java
                )
            )
        ).thenReturn(true)
        decrypt.execute(args)
        val fileCaptor = ArgumentCaptor.forClass(
            File::class.java
        )
        verify(fileResolveHelper).fileExistsAndHasExtension(
            ArgumentMatchers.eq(AppProperties.ENC_FILE_EXTENSION), ArgumentMatchers.any(
                File::class.java
            )
        )
        verify(cryptoHelper)
            .decryptFile(fileCaptor.capture(), ArgumentMatchers.eq(silentFlag))
        Assert.assertEquals(encFileName, fileCaptor.value.name)
    }

    private fun decryptLog(logFileName: String) {
        whenever(fileResolveHelper.resolveAsLogFilePath(ArgumentMatchers.anyString()))
            .thenReturn(
                File(logFileName)
            )
        val args: MutableList<String> = ArrayList()
        args.add(logFileName)
        decrypt.execute(args)
        val fileCaptor = ArgumentCaptor.forClass(
            File::class.java
        )
        verify(cryptoHelper).decryptLog(fileCaptor.capture())
        Assert.assertEquals(logFileName, fileCaptor.value.name)
    }
}