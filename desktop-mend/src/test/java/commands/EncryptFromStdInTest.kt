package commands

import co.samco.mend4.core.Settings
import co.samco.mend4.desktop.commands.Encrypt
import co.samco.mend4.desktop.commands.EncryptFromStdIn
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

class EncryptFromStdInTest : TestBase() {
    private lateinit var encrypt: EncryptFromStdIn

    @Before
    override fun setup() {
        super.setup()
        try {
            whenever(settings.getValue(Settings.Name.ENCDIR)).thenReturn("encdir")
            whenever(settings.getValue(Settings.Name.LOGDIR)).thenReturn("decdir")
        } catch (e: IOException) {
            e.printStackTrace()
        }
        encrypt = EncryptFromStdIn(
            settings = settings,
            settingsHelper = settingsHelper,
            log = log,
            strings = strings,
            cryptoHelper = cryptoHelper,
            inputHelper = inputHelper,
            fileResolveHelper = fileResolveHelper
        )
    }

    @Test
    fun testCommandWithSettingsDependencies() {
        super.testCommandWithNoSettingsDependencies(encrypt)
    }

    @Test
    fun encryptFromStdIn() {
        encryptFromStdIn(false)
    }

    @Test
    fun encryptFromStdInAppend() {
        encryptFromStdIn(true)
    }

    private fun encryptFromStdIn(appendFlag: Boolean) {
        val input = "hi\rhi again\nhi once more"
        val inputStream: InputStream =
            ByteArrayInputStream(input.toByteArray(StandardCharsets.UTF_8))
        System.setIn(inputStream)
        encrypt.execute(if (appendFlag) listOf(Encrypt.APPEND_FLAG) else emptyList())
        val encryptOutput: ArgumentCaptor<CharArray> =
            ArgumentCaptor.forClass(CharArray::class.java)
        verify(cryptoHelper).encryptTextToLog(encryptOutput.capture(), eq(appendFlag))
        val expectedText =
            "hi" + System.getProperty("line.separator") + "hi again" + System.getProperty("line.separator") + "hi once more"
        Assert.assertEquals(expectedText, String(encryptOutput.value))
    }
}