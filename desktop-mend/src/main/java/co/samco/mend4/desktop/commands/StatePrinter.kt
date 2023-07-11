package co.samco.mend4.desktop.commands

import co.samco.mend4.core.AppProperties
import co.samco.mend4.core.Settings
import co.samco.mend4.core.exception.CorruptSettingsException
import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.dao.OSDao
import co.samco.mend4.desktop.dao.SettingsDao
import co.samco.mend4.desktop.helper.FileResolveHelper
import co.samco.mend4.desktop.helper.SettingsHelper
import co.samco.mend4.desktop.output.PrintStreamProvider
import org.apache.commons.io.FilenameUtils
import java.io.File
import java.io.IOException
import java.util.function.Function
import javax.inject.Inject

class StatePrinter @Inject constructor(
    private val strings: I18N,
    private val log: PrintStreamProvider,
    private val settingsHelper: SettingsHelper,
    private val settings: SettingsDao,
    private val fileResolveHelper: FileResolveHelper,
    private val osDao: OSDao
) : CommandBase() {
    private lateinit var arg: String

    private val behaviourChain = listOf(
        Function { a: List<String> -> getArg(a) },
        Function { a: List<String> -> checkArgIsFlag(a) },
        Function { a: List<String> -> printFromArg(a) },
    )

    private fun getArg(args: List<String>): List<String>? {
        if (args.size != 1) {
            log.err().println(strings.getf("General.invalidArgNum", COMMAND_NAME))
            return null
        }
        arg = args[0]
        return args
    }

    private fun checkArgIsFlag(args: List<String>): List<String>? {
        try {
            when (arg) {
                ALL_FLAG -> printSettings()
                LOGS_FLAG -> printLogs()
                ENCS_FLAG -> printEncs()
                else -> return args
            }
        } catch (t: Throwable) {
            failWithMessage(log, t.message)
        }
        return null
    }

    private fun printFromArg(args: List<String>): List<String>? {
        val value = SettingsDao.ALL_SETTINGS
            .filter { it.encodedName == arg }
            .map { settingsHelper.getSettingValueWrapped(it) }
            .firstOrNull()

        if (value != null) log.out().println(value)
        else log.err().println(strings.getf("StatePrinter.settingNotFound", arg))

        return null
    }

    private fun printEncs() {
        val encDir = settings.getValue(SettingsDao.ENC_DIR)
            ?.let { File(it) }
            ?: throw throw CorruptSettingsException(
                strings.getf(
                    "General.dirRequired",
                    SettingsDao.CURRENT_LOG,
                    COMMAND_NAME
                )
            )
        fileResolveHelper.assertDirectoryExists(encDir)

        osDao.listFiles(encDir)
            ?.filter { FilenameUtils.getExtension(it.name) == AppProperties.ENC_FILE_EXTENSION }
            ?.forEach { log.out().println(FilenameUtils.getBaseName(it.absolutePath)) }
    }

    @Throws(CorruptSettingsException::class, IOException::class)
    private fun printLogs() {
        val logDir = settings.getValue(SettingsDao.LOG_DIR)
            ?.let { File(it) }
            ?: throw CorruptSettingsException(
                strings.getf(
                    "General.dirRequired",
                    SettingsDao.LOG_DIR,
                    COMMAND_NAME
                )
            )

        fileResolveHelper.assertDirectoryExists(logDir)

        osDao.listFiles(logDir)
            ?.filter { FilenameUtils.getExtension(it.name) == AppProperties.LOG_FILE_EXTENSION }
            ?.forEach { log.out().println(FilenameUtils.getBaseName(it.absolutePath)) }
    }

    private fun printSettings() {
        SettingsDao.ALL_SETTINGS.forEach { log.out().println(formatSettingValue(it)) }
    }

    private fun formatSettingValue(name: Settings.Name): String {
        val value: String = settingsHelper.getSettingValueWrapped(name)
        val sb = StringBuilder()
        sb.append(name.toString())
        sb.append("\t")
        sb.append(value)
        return sb.toString()
    }

    public override fun execute(args: List<String>) {
        executeBehaviourChain(behaviourChain, args)
    }

    override val usageText: String
        get() = strings.getf(
            "StatePrinter.usage",
            COMMAND_NAME,
            ALL_FLAG,
            LOGS_FLAG,
            ENCS_FLAG,
            ALL_FLAG,
            LOGS_FLAG,
            ENCS_FLAG,
            settingsHelper.settingDescriptions
        )
    override val descriptionText: String
        get() = strings["StatePrinter.description"]
    override val commandAliases: List<String>
        get() = listOf(COMMAND_NAME)

    companion object {
        const val COMMAND_NAME = "get"
        const val ALL_FLAG = "-a"
        const val LOGS_FLAG = "-l"
        const val ENCS_FLAG = "-e"
    }
}