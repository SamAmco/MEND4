package co.samco.mend4.desktop.commands

import co.samco.mend4.core.Settings
import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.dao.SettingsDao
import co.samco.mend4.desktop.helper.SettingsHelper
import co.samco.mend4.desktop.output.PrintStreamProvider
import java.io.IOException
import java.util.function.Function
import javax.inject.Inject

class SetProperty @Inject constructor(
    private val log: PrintStreamProvider,
    private val strings: I18N,
    private val settings: SettingsDao,
    private val settingsHelper: SettingsHelper
) : CommandBase() {

    private lateinit var propertyName: String
    private lateinit var value: String

    private val behaviourChain = listOf(
        Function { a: List<String> -> assertCorrectNumArgs(a) },
        Function { a: List<String> -> assertPropertyExists(a) },
        Function { a: List<String> -> setProperty(a) }
    )

    private fun assertCorrectNumArgs(args: List<String>): List<String>? {
        if (args.size != 2) {
            log.err().println(strings.getf("General.invalidArgNum", COMMAND_NAME))
            return null
        }
        return args
    }

    private fun assertPropertyExists(args: List<String>): List<String>? {
        propertyName = args[0]
        value = args[1]
        if (SettingsDao.ALL_SETTINGS.none { it.encodedName == propertyName }) {
            log.err().println(strings.getf("SetProperty.notRecognised", propertyName))
            log.err().println()
            log.err().println(usageText)
            return null
        }
        return args
    }

    private fun setProperty(args: List<String>): List<String>? {
        try {
            val name = SettingsDao.ALL_SETTINGS.first { it.encodedName == propertyName }
            settings.setValue(name, value)
            log.err().println(strings.getf("SetProperty.successful", propertyName, value))
        } catch (e: IOException) {
            failWithMessage(log, e.message)
        }
        return null
    }

    public override fun execute(args: List<String>) {
        executeBehaviourChain(behaviourChain, args)
    }

    override val usageText: String
        get() = strings.getf("SetProperty.usage", COMMAND_NAME, settingsHelper.settingDescriptions)
    override val descriptionText: String
        get() = strings["SetProperty.description"]
    override val commandAliases: List<String>
        get() = listOf(COMMAND_NAME)

    companion object {
        const val COMMAND_NAME = "set"
    }
}