package commands

import co.samco.mend4.core.Settings
import co.samco.mend4.desktop.commands.Encrypt
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

class EncryptTest : TestBase() {
    private var encrypt: Encrypt? = null

    @Before
    override fun setup() {
        super.setup()
        encrypt = Encrypt(
            settings,
            settingsHelper,
            log,
            strings,
            cryptoHelper,
            inputHelper,
            fileResolveHelper
        )
    }

    @Test
    fun testCommandWithSettingsDependencies() {
        super.testCommandWithNoSettingsDependencies(encrypt!!)
    }

    private fun setLogAndEncDir() {
        whenever(settings.getValue(Settings.Name.ENCDIR)).thenReturn("encdir")
        whenever(settings.getValue(Settings.Name.LOGDIR)).thenReturn("decdir")
    }

    @Test
    fun encryptViaTextEditor() {
        setLogAndEncDir()
        Thread {
            while (true) {
                encrypt!!.onClose()
            }
        }.start()
        encrypt!!.execute(emptyList())
        verify(inputHelper).createInputProviderAndRegisterListener(encrypt!!)
        encrypt!!.onWrite("hi".toCharArray())
        verify(cryptoHelper).encryptTextToLog("hi".toCharArray(), false)
    }

    @Test
    fun encryptViaTextEditorAppend() {
        setLogAndEncDir()
        Thread {
            while (true) {
                encrypt!!.onClose()
            }
        }.start()
        encrypt!!.execute(listOf(Encrypt.APPEND_FLAG))
        verify(inputHelper).createInputProviderAndRegisterListener(encrypt!!)
        encrypt!!.onWrite("hi".toCharArray())
        verify(cryptoHelper).encryptTextToLog("hi".toCharArray(), true)
    }

    @Test
    fun encryptFromArg() {
        setLogAndEncDir()
        encrypt!!.execute(listOf(Encrypt.FROM_ARG_FLAG, "hi"))
        verify(cryptoHelper).encryptTextToLog("hi".toCharArray(), false)
    }

    @Test
    fun encryptFromArgAppend() {
        setLogAndEncDir()
        encrypt!!.execute(listOf(Encrypt.APPEND_FLAG, Encrypt.FROM_ARG_FLAG, "hi"))
        verify(cryptoHelper).encryptTextToLog("hi".toCharArray(), true)
    }

    @Test
    fun encryptFromArgAppendFlipped() {
        setLogAndEncDir()
        encrypt!!.execute(listOf(Encrypt.FROM_ARG_FLAG, Encrypt.APPEND_FLAG, "hi"))
        verify(cryptoHelper).encryptTextToLog("hi".toCharArray(), true)
    }

    @Test
    fun encryptFromArgMalformed() {
        setLogAndEncDir()
        encrypt!!.execute(listOf(Encrypt.FROM_ARG_FLAG, "a", "b"))
        verify(err).println(strings.getf("Encrypt.badDataArgs", Encrypt.FROM_ARG_FLAG))
    }

    @Test
    fun encryptTooManyArgs() {
        setLogAndEncDir()
        encrypt!!.execute(listOf("a", "b", "c"))
        verify(err).println(strings.getf("General.invalidArgNum", Encrypt.COMMAND_NAME))
    }

    @Test
    fun encryptFile() {
        setLogAndEncDir()
        val fileName = "hi"
        whenever(fileResolveHelper.resolveFile(ArgumentMatchers.eq(fileName)))
            .thenReturn(File(fileName))
        encrypt!!.execute(listOf(fileName))
        val outCaptor = ArgumentCaptor.forClass(
            File::class.java
        )
        verify(cryptoHelper)
            .encryptFile(outCaptor.capture(), ArgumentMatchers.isNull<Any>() as String)
        Assert.assertTrue(outCaptor.value.name == fileName)
    }

    @Test
    fun encryptFileWithName() {
        setLogAndEncDir()
        val fileName1 = "hi1"
        val fileName2 = "hi2"
        whenever(fileResolveHelper.resolveFile(ArgumentMatchers.eq(fileName1))).thenReturn(
            File(fileName1)
        )
        encrypt!!.execute(listOf(fileName1, fileName2))
        val outCaptor = ArgumentCaptor.forClass(
            File::class.java
        )
        verify(cryptoHelper)
            .encryptFile(outCaptor.capture(), ArgumentMatchers.eq(fileName2))
        Assert.assertTrue(outCaptor.value.name == fileName1)
    }
}