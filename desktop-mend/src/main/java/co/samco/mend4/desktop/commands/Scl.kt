package co.samco.mend4.desktop.commands

import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.dao.SettingsDao
import co.samco.mend4.desktop.output.PrintStreamProvider
import java.io.IOException
import javax.inject.Inject

class Scl @Inject constructor(
    private val log: PrintStreamProvider,
    private val strings: I18N,
    private val settings: SettingsDao
) : CommandBase() {
    companion object {
        const val COMMAND_NAME = "scl"
    }

    override fun execute(args: List<String>) {
        if (args.size != 1) {
            log.err().println(strings.getf("General.invalidArgNum", COMMAND_NAME))
            return
        }

        try {
            val value = args[0]
            settings.setValue(SettingsDao.CURRENT_LOG, value)
            log.err().println(strings.getf("SetProperty.successful", SettingsDao.CURRENT_LOG.encodedName, value))
        } catch (e: IOException) {
            failWithMessage(log, e.message)
        }
    }

    override val usageText: String
        get() = strings.getf(
            "Scl.usage",
            COMMAND_NAME,
            SetProperty.COMMAND_NAME,
            SettingsDao.CURRENT_LOG
        )
    override val descriptionText: String
        get() = strings["Scl.description"]
    override val commandAliases: List<String>
        get() = listOf(COMMAND_NAME)
}