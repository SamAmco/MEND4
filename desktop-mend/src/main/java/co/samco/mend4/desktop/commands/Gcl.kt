package co.samco.mend4.desktop.commands

import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.dao.SettingsDao
import co.samco.mend4.desktop.output.PrintStreamProvider
import javax.inject.Inject

class Gcl @Inject constructor(
    private val log: PrintStreamProvider,
    private val strings: I18N,
    private val settings: SettingsDao
) : CommandBase() {

    companion object {
        const val COMMAND_NAME = "gcl"
    }

    override fun execute(args: List<String>) {
        if (args.isNotEmpty()) {
            log.err().println(strings.getf("General.invalidArgNum", COMMAND_NAME))
            log.out().println()
        }

        log.out().println(settings.getValue(SettingsDao.CURRENT_LOG))
    }

    override val usageText: String
        get() = strings.getf(
            "Gcl.usage",
            SettingsDao.CURRENT_LOG
        )
    override val descriptionText: String
        get() = strings["Gcl.description"]

    override val commandAliases: List<String>
        get() = listOf(COMMAND_NAME)
}