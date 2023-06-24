package commands

import co.samco.mend4.core.AppProperties
import co.samco.mend4.core.Settings
import co.samco.mend4.desktop.commands.StatePrinter
import junit.framework.Assert.assertTrue
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

class StatePrinterTest : TestBase() {
    private lateinit var statePrinter: StatePrinter

    @Before
    override fun setup() {
        super.setup()
        statePrinter = StatePrinter(
            strings = strings,
            log = log,
            settingsHelper = settingsHelper,
            settings = settings,
            fileResolveHelper = fileResolveHelper,
            osDao = osDao
        )
    }

    @Test
    fun tooManyArgs() {
        statePrinter.execute(listOf("a", "b"))
        val errStr = strings.getf("General.invalidArgNum", StatePrinter.COMMAND_NAME)
        verify(err).println(eq(errStr))
    }

    @Test
    fun tooFewArgs() {
        statePrinter.execute(emptyList())
        val errStr = strings.getf("General.invalidArgNum", StatePrinter.COMMAND_NAME)
        verify(err).println(eq(errStr))
    }

    @Test
    fun allFlag() {
        whenever(settingsHelper.getSettingValueWrapped(any()))
            .thenReturn("")
        val outCaptor = argumentCaptor<String>()
        statePrinter.execute(listOf(StatePrinter.ALL_FLAG))
        verify(out).println(outCaptor.capture())
        for (n in Settings.Name.values()) {
            assertTrue(outCaptor.firstValue.contains(n.toString()))
        }
    }

    @Test
    fun logFlag() {
        val logDir = "dir"
        whenever(settings.getValue(eq(Settings.Name.LOGDIR))).thenReturn(logDir)
        filePrintTest(StatePrinter.LOGS_FLAG, AppProperties.LOG_FILE_EXTENSION)
    }

    @Test
    fun encFlag() {
        val encDir = "dir"
        whenever(settings.getValue(eq(Settings.Name.ENCDIR))).thenReturn(encDir)
        filePrintTest(StatePrinter.ENCS_FLAG, AppProperties.ENC_FILE_EXTENSION)
    }

    @Test
    fun printSetting() {
        val name = Settings.Name.values()[0]
        val value = "hi"
        whenever(settingsHelper.getSettingValueWrapped(eq(name))).thenReturn(value)
        statePrinter.execute(listOf(name.toString()))
        verify(out).println(eq(value))
    }

    @Test
    fun fallback() {
        val unknown = "unknown property"
        statePrinter.execute(listOf(unknown))
        verify(err)
            .println(eq(strings.getf("StatePrinter.settingNotFound", unknown)))
    }

    private fun filePrintTest(flag: String, extension: String) {
        val files = arrayOf(File("f1"), File("f2"))
        whenever(osDao.listFiles(any())).thenReturn(files)
        val outCaptor = argumentCaptor<String>()

        statePrinter.execute(listOf(flag))

        verify(out).println(outCaptor.capture())
        for (f in files) {
            Assert.assertTrue(outCaptor.firstValue.contains(f.name))
        }
    }
}