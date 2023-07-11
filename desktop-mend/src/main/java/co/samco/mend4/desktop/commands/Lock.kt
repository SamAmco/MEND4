package co.samco.mend4.desktop.commands

import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.dao.OSDao
import co.samco.mend4.desktop.dao.SettingsDao
import co.samco.mend4.desktop.helper.FileResolveHelper
import co.samco.mend4.desktop.helper.SettingsHelper
import co.samco.mend4.desktop.helper.ShredHelper
import co.samco.mend4.desktop.output.PrintStreamProvider
import java.io.File
import java.util.function.Function
import javax.inject.Inject

class Lock @Inject constructor(
    private val log: PrintStreamProvider,
    private val settingsHelper: SettingsHelper,
    private val shredHelper: ShredHelper,
    private val strings: I18N,
    private val fileResolveHelper: FileResolveHelper,
    private val osDao: OSDao
) : CommandBase() {
    companion object {
        private const val COMMAND_NAME = "lock"
    }

    private val behaviourChain = listOf(
        Function { a: List<String> -> assertSettingsPresent(a) },
        Function { a: List<String> -> performLock(a) }
    )

    private fun printIfNotNull(message: String?) {
        if (message != null) {
            log.err().println(message)
        }
    }

    private fun printLockStatus(messageIfUnlocked: String?, messageIfLocked: String) {
        val privateKeyFile: File = fileResolveHelper.privateKeyFile
        val locked: Boolean = !osDao.exists(privateKeyFile)
        printIfNotNull(if (locked) messageIfLocked else messageIfUnlocked)
    }

    private fun assertSettingsPresent(args: List<String>): List<String>? {
        try {
            settingsHelper.assertRequiredSettingsExist(
                arrayOf(SettingsDao.SHRED_COMMAND),
                COMMAND_NAME
            )
        } catch (t: Throwable) {
            failWithMessage(log, t.message)
            return null
        }
        return args
    }

    private fun performLock(args: List<String>): List<String>? {
        try {
            printLockStatus(null, strings["Lock.notUnlocked"])
            shredHelper.tryShredFile(fileResolveHelper.privateKeyFile.absolutePath)
            shredHelper.tryShredFile(fileResolveHelper.publicKeyFile.absolutePath)
            printLockStatus(strings["Lock.lockFailed"], strings["Lock.locked"])
        } catch (t: Throwable) {
            failWithMessage(log, t.message)
        }
        return null
    }

    public override fun execute(args: List<String>) {
        executeBehaviourChain(behaviourChain, args)
    }

    override val usageText: String
        get() = strings.getf("Lock.usage", COMMAND_NAME)
    override val descriptionText: String
        get() = strings["Lock.description"]
    override val commandAliases: List<String>
        get() = listOf(COMMAND_NAME)
}