package co.samco.mend4.desktop.commands

import co.samco.mend4.core.Settings
import co.samco.mend4.core.exception.CorruptSettingsException
import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.helper.SettingsHelper
import co.samco.mend4.desktop.helper.ShredHelper
import co.samco.mend4.desktop.output.PrintStreamProvider
import java.util.function.Function
import javax.inject.Inject

class Clean @Inject constructor(
    private val strings: I18N,
    private val log: PrintStreamProvider,
    private val settingsHelper: SettingsHelper,
    private val shredHelper: ShredHelper,
    private val settings: Settings
) : Command() {
    private val behaviourChain = listOf(
        Function { a: List<String> -> assertSettingsPresent(a) },
        Function { a: List<String> -> performClean(a) }
    )

    private fun assertSettingsPresent(args: List<String>): List<String>? {
        try {
            settingsHelper.assertRequiredSettingsExist(
                arrayOf(
                    Settings.Name.DECDIR, Settings.Name.SHREDCOMMAND
                ),
                COMMAND_NAME
            )
        } catch (t: Throwable) {
            failWithMessage(log, t.message)
            return null
        }
        return args
    }

    private fun performClean(args: List<String>): List<String>? {
        try {
            val decDir = settings.getValue(Settings.Name.DECDIR)
                ?: throw CorruptSettingsException(
                    strings["General.dirRequired"],
                    Settings.Name.DECDIR
                )

            shredHelper.shredFilesInDirectory(decDir)
            log.err().println(strings["Clean.cleanComplete"])
        } catch (t: Throwable) {
            failWithMessage(log, t.message)
        }
        return null
    }

    public override fun execute(args: List<String>) {
        executeBehaviourChain(behaviourChain, args)
    }

    override val usageText: String
        get() = strings.getf("Clean.usage", COMMAND_NAME)
    override val descriptionText: String
        get() = strings.getf("Clean.description", Settings.Name.SHREDCOMMAND.toString())

    override val commandAliases: List<String>
        get() = listOf(COMMAND_NAME)

    companion object {
        const val COMMAND_NAME = "clean"
    }
}