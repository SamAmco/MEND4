package commands

import co.samco.mend4.core.AppProperties
import co.samco.mend4.desktop.commands.StatePrinter
import co.samco.mend4.desktop.dao.SettingsDao
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.apache.commons.io.FilenameUtils
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
        verify(out, times(SettingsDao.ALL_SETTINGS.size)).println(outCaptor.capture())
        for (n in SettingsDao.ALL_SETTINGS) {
            assertTrue(outCaptor.allValues.any { it.contains(n.toString()) })
        }
    }

    @Test
    fun logFlag() {
        val logDir = "dir"
        whenever(settings.getValue(eq(SettingsDao.LOG_DIR))).thenReturn(logDir)
        filePrintTest(StatePrinter.LOGS_FLAG, AppProperties.LOG_FILE_EXTENSION)
    }

    @Test
    fun encFlag() {
        val encDir = "dir"
        whenever(settings.getValue(eq(SettingsDao.ENC_DIR))).thenReturn(encDir)
        filePrintTest(StatePrinter.ENCS_FLAG, AppProperties.ENC_FILE_EXTENSION)
    }

    @Test
    fun printSetting() {
        val name = SettingsDao.ALL_SETTINGS[0]
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
        val files = arrayOf(File("f1.$extension"), File("f2.$extension"))
        whenever(osDao.listFiles(any())).thenReturn(files)
        val outCaptor = argumentCaptor<String>()

        statePrinter.execute(listOf(flag))

        verify(out, times(2)).println(outCaptor.capture())

        for (f in files) {
            assertTrue(
                outCaptor.allValues.contains(
                    FilenameUtils.getBaseName(f.absolutePath)
                )
            )
        }
    }
}