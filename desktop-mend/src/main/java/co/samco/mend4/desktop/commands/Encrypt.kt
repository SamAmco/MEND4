package co.samco.mend4.desktop.commands

import co.samco.mend4.core.Settings
import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.dao.SettingsDao
import co.samco.mend4.desktop.helper.CryptoHelper
import co.samco.mend4.desktop.helper.FileResolveHelper
import co.samco.mend4.desktop.helper.InputHelper
import co.samco.mend4.desktop.helper.SettingsHelper
import co.samco.mend4.desktop.input.InputListener
import co.samco.mend4.desktop.output.PrintStreamProvider
import java.util.function.Function
import javax.inject.Inject

open class Encrypt @Inject constructor(
    protected val settings: SettingsDao,
    protected val settingsHelper: SettingsHelper,
    protected val log: PrintStreamProvider,
    protected val strings: I18N,
    protected val cryptoHelper: CryptoHelper,
    private val inputHelper: InputHelper,
    protected val fileResolveHelper: FileResolveHelper
) : CommandBase(), InputListener {

    @JvmField
    protected var dropHeader = false
    private var waitingForInputListener = false
    private val behaviourChain = listOf(
        Function { a: List<String> -> assertSettingsPresent(a) },
        Function { a: List<String> -> shouldDropHeader(a) },
        Function { a: List<String> -> shouldEncryptFromTextEditor(a) },
        Function { a: List<String> -> shouldEncryptFromArg(a) },
        Function { a: List<String> -> tooManyArgs(a) },
        Function { a: List<String> -> encryptFile(a) },
        Function { _: List<String> -> unknownBehaviour() }
    )

    public override fun execute(args: List<String>) {
        executeBehaviourChain(behaviourChain, args)
        try {
            while (waitingForInputListener) {
                Thread.sleep(100)
            }
        } catch (e: InterruptedException) {
            failWithMessage(log, strings["Encrypt.threadInterrupt"])
        }
    }

    protected fun assertSettingsPresent(args: List<String>): List<String>? {
        try {
            settingsHelper.assertRequiredSettingsExist(
                arrayOf(
                    Settings.Name.ASYMMETRIC_CIPHER_NAME,
                    Settings.Name.ASYMMETRIC_CIPHER_TRANSFORM,
                    Settings.Name.ASYMMETRIC_KEY_SIZE,
                    Settings.Name.PUBLIC_KEY,
                    SettingsDao.ENC_DIR,
                    SettingsDao.LOG_DIR
                ),
                COMMAND_NAME
            )
        } catch (t: Throwable) {
            failWithMessage(log, t.message)
            return null
        }
        return args
    }

    protected fun shouldDropHeader(args: List<String>): List<String>? {
        val newArgs: MutableList<String> = ArrayList(args)
        if (newArgs.contains(APPEND_FLAG)) {
            dropHeader = true
            newArgs.remove(APPEND_FLAG)
        }
        return newArgs
    }

    private fun shouldEncryptFromTextEditor(args: List<String>): List<String>? {
        if (args.isEmpty()) {
            inputHelper.createInputProviderAndRegisterListener(this)
            waitingForInputListener = true
            return null
        }
        return args
    }

    private fun shouldEncryptFromArg(args: List<String>): List<String>? {
        if (args.contains(FROM_ARG_FLAG)) {
            val index = args.indexOf(FROM_ARG_FLAG)
            if (args.size != index + 2) {
                log.err().println(strings.getf("Encrypt.badDataArgs", FROM_ARG_FLAG))
            } else {
                encryptTextToLog(args[index + 1].toCharArray())
            }
            return null
        }
        return args
    }

    private fun tooManyArgs(args: List<String>): List<String>? {
        if (args.size > 2) {
            log.err().println(strings.getf("General.invalidArgNum", COMMAND_NAME))
            return null
        }
        return args
    }

    private fun encryptFile(args: List<String>): List<String>? {
        try {
            val name: String? = args.getOrNull(1)
            cryptoHelper.encryptFile(fileResolveHelper.resolveFile(args[0]), name)
        } catch (t: Throwable) {
            failWithMessage(log, t.message)
        }
        return null
    }

    private fun unknownBehaviour(): List<String>? {
        log.err().println(strings.getf("Encrypt.malformedCommand", COMMAND_NAME))
        return null
    }

    private fun encryptTextToLog(text: CharArray) {
        try {
            cryptoHelper.encryptTextToLog(text, dropHeader)
        } catch (t: Throwable) {
            failWithMessage(log, t.message)
        }
    }

    override val usageText: String
        get() = strings.getf("Encrypt.usage", COMMAND_NAME, APPEND_FLAG, FROM_ARG_FLAG)
    override val descriptionText: String
        get() = strings["Encrypt.description"]
    override val commandAliases: List<String>
        get() = listOf(COMMAND_NAME)

    override fun onWrite(text: CharArray) {
        encryptTextToLog(text)
    }

    override fun onClose() {
        waitingForInputListener = false
    }

    companion object {
        const val COMMAND_NAME = "enc"
        const val APPEND_FLAG = "-a"
        const val FROM_ARG_FLAG = "-d"
    }
}