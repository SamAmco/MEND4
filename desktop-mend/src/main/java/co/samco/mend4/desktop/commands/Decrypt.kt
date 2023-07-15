package co.samco.mend4.desktop.commands

import co.samco.mend4.core.AppProperties
import co.samco.mend4.core.Settings
import co.samco.mend4.core.exception.CorruptSettingsException
import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.dao.SettingsDao
import co.samco.mend4.desktop.exception.MendLockedException
import co.samco.mend4.desktop.helper.CryptoHelper
import co.samco.mend4.desktop.helper.FileResolveHelper
import co.samco.mend4.desktop.helper.SettingsHelper
import co.samco.mend4.desktop.output.PrintStreamProvider
import java.io.File
import java.util.function.Function
import javax.inject.Inject

class Decrypt @Inject constructor(
    private val log: PrintStreamProvider,
    private val strings: I18N,
    private val settingsHelper: SettingsHelper,
    private val cryptoHelper: CryptoHelper,
    private val fileResolveHelper: FileResolveHelper,
    private val settings: SettingsDao
) : CommandBase() {

    companion object {
        const val COMMAND_NAME = "dec"
        const val SILENT_FLAG = "-s"
    }

    private var silent = false
    private lateinit var fileIdentifier: String
    private val behaviourChain = listOf(
        Function { a: List<String> -> assertSettingsPresent(a) },
        Function { a: List<String> -> checkShouldBeSilent(a) },
        Function { a: List<String> -> getFileIdentifier(a) },
        Function { a: List<String> -> tryResolveFileAsEncId(a) },
        Function { _: List<String> -> tryResolveFileAsLog() }
    )

    private fun assertSettingsPresent(args: List<String>): List<String>? {
        try {
            settingsHelper.assertRequiredSettingsExist(
                arrayOf(
                    Settings.Name.ASYMMETRIC_CIPHER_NAME,
                    Settings.Name.ASYMMETRIC_CIPHER_TRANSFORM,
                    Settings.Name.ASYMMETRIC_KEY_SIZE,
                    Settings.Name.PW_KEY_FACTORY_ITERATIONS,
                    Settings.Name.PW_KEY_FACTORY_MEMORY_KB,
                    Settings.Name.PW_KEY_FACTORY_PARALLELISM,
                    Settings.Name.PW_KEY_FACTORY_SALT,
                    Settings.Name.PW_PRIVATE_KEY_CIPHER_IV,
                    Settings.Name.ENCRYPTED_PRIVATE_KEY,
                    Settings.Name.PUBLIC_KEY,
                    SettingsDao.LOG_DIR,
                    SettingsDao.ENC_DIR,
                    SettingsDao.DEC_DIR,
                    SettingsDao.SHRED_COMMAND
                ),
                COMMAND_NAME
            )
        } catch (t: Throwable) {
            failWithMessage(log, t.message)
            return null
        }
        return args
    }

    private fun checkShouldBeSilent(args: List<String>): List<String>? {
        val newArgs: MutableList<String> = ArrayList(args)
        if (newArgs.contains(SILENT_FLAG)) {
            silent = true
            newArgs.remove(SILENT_FLAG)
        }
        return newArgs
    }

    private fun getFileIdentifier(args: List<String>): List<String>? {
        if (args.isEmpty()) {
            log.err().println(strings["Decrypt.noFile"])
            log.err().println(usageText)
            return null
        }
        fileIdentifier = args[0]
        return args
    }

    private fun tryResolveFileAsEncId(args: List<String>): List<String>? {
        try {
            val file: File = fileResolveHelper.resolveAsEncFilePath(fileIdentifier)
            val valid = fileResolveHelper
                .fileExistsAndHasExtension(AppProperties.ENC_FILE_EXTENSION, file)

            return if (valid) {
                val decDir = settings.getValue(SettingsDao.DEC_DIR)
                    ?: throw CorruptSettingsException(
                        strings.getf(
                            "General.propertyNotSet",
                            SettingsDao.DEC_DIR,
                            COMMAND_NAME
                        )
                    )
                cryptoHelper.decryptFile(file, decDir, silent)
                null
            } else args
        } catch (e: MendLockedException) {
            failWithMessage(log, strings.getf("Decrypt.mendLocked", Unlock.COMMAND_NAME))
        } catch (e: Throwable) {
            failWithMessage(log, e.message)
        }
        return null
    }

    private fun tryResolveFileAsLog(): List<String>? {
        try {
            val file = fileResolveHelper.resolveAsLogFilePath(fileIdentifier)
            fileResolveHelper.assertFileExistsAndHasExtension(
                fileIdentifier,
                AppProperties.LOG_FILE_EXTENSION,
                file
            )
            cryptoHelper.decryptLog(file)
        } catch (e: MendLockedException) {
            failWithMessage(log, strings.getf("Decrypt.mendLocked", Unlock.COMMAND_NAME))
        } catch (t: Throwable) {
            failWithMessage(log, t.message)
        }
        return null
    }

    public override fun execute(args: List<String>) {
        executeBehaviourChain(behaviourChain, args)
    }

    override val usageText: String
        get() = strings.getf("Decrypt.usage", COMMAND_NAME, SILENT_FLAG)
    override val descriptionText: String
        get() = strings["Decrypt.description"]
    override val commandAliases: List<String>
        get() = listOf(COMMAND_NAME)
}