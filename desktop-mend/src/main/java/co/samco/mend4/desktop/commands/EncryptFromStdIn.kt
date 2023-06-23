package co.samco.mend4.desktop.commands

import co.samco.mend4.core.Settings
import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.helper.CryptoHelper
import co.samco.mend4.desktop.helper.FileResolveHelper
import co.samco.mend4.desktop.helper.InputHelper
import co.samco.mend4.desktop.helper.SettingsHelper
import co.samco.mend4.desktop.output.PrintStreamProvider
import java.util.Scanner
import java.util.function.Function
import javax.inject.Inject

class EncryptFromStdIn @Inject constructor(
    settings: Settings,
    settingsHelper: SettingsHelper,
    log: PrintStreamProvider,
    strings: I18N,
    cryptoHelper: CryptoHelper,
    inputHelper: InputHelper,
    fileResolveHelper: FileResolveHelper
) : Encrypt(
    settings = settings,
    settingsHelper = settingsHelper,
    log = log,
    strings = strings,
    cryptoHelper = cryptoHelper,
    inputHelper = inputHelper,
    fileResolveHelper = fileResolveHelper
) {
    private val behaviourChain = listOf(
        Function { a: List<String> -> assertSettingsPresent(a) },
        Function { a: List<String> -> shouldDropHeader(a) },
        Function { a: List<String> -> encryptFromInput(a) }
    )

    private fun encryptFromInput(args: List<String>): List<String>? {
        val scanner = Scanner(System.`in`)
        val sb = StringBuilder()
        while (scanner.hasNextLine()) {
            sb.append(scanner.nextLine())
            if (scanner.hasNextLine()) {
                sb.append(strings.newLine)
            }
        }
        scanner.close()
        try {
            cryptoHelper.encryptTextToLog(sb.toString().toCharArray(), dropHeader)
        } catch (t: Throwable) {
            failWithMessage(log, t.message)
        }
        return null
    }

    override fun execute(args: List<String>) {
        executeBehaviourChain(behaviourChain, args)
    }

    override val commandAliases: List<String>
        get() = listOf(COMMAND_NAME)
    override val usageText: String
        get() = strings.getf("EncryptFromStdIn.usage", COMMAND_NAME, APPEND_FLAG)
    override val descriptionText: String
        get() = strings["EncryptFromStdIn.description"]

    companion object {
        const val COMMAND_NAME = "enci"
    }
}