package co.samco.mend4.desktop.commands

import co.samco.mend4.core.AppProperties
import co.samco.mend4.core.Settings
import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.dao.OSDao
import co.samco.mend4.desktop.exception.MendLockedException
import co.samco.mend4.desktop.helper.FileResolveHelper
import co.samco.mend4.desktop.helper.MergeHelper
import co.samco.mend4.desktop.helper.SettingsHelper
import co.samco.mend4.desktop.output.PrintStreamProvider
import java.io.File
import java.util.function.Function
import javax.inject.Inject

class Merge @Inject constructor(
    private val strings: I18N,
    private val log: PrintStreamProvider,
    private val fileResolveHelper: FileResolveHelper,
    private val settingsHelper: SettingsHelper,
    private val mergeHelper: MergeHelper
) : CommandBase() {
    private val behaviourChain = listOf(
        Function { a: List<String> -> assertSettingsPresent(a) },
        Function { a: List<String> -> assertCorrectNumOfArgs(a) },
        Function { a: List<String> -> tryMergeInPlace(a) },
        Function { a: List<String> -> tryMergeToNew(a) }
    )

    private fun assertSettingsPresent(args: List<String>): List<String>? {
        try {
            settingsHelper.assertRequiredSettingsExist(
                arrayOf(
                    Settings.Name.ASYMMETRIC_CIPHER_NAME,
                    Settings.Name.ASYMMETRIC_CIPHER_TRANSFORM,
                    Settings.Name.ASYMMETRIC_KEY_SIZE,
                    Settings.Name.PW_KEY_FACTORY_ALGORITHM,
                    Settings.Name.PW_KEY_FACTORY_ITERATIONS,
                    Settings.Name.PW_KEY_FACTORY_SALT,
                    Settings.Name.PW_PRIVATE_KEY_CIPHER_IV,
                    Settings.Name.ENCRYPTED_PRIVATE_KEY,
                    Settings.Name.PUBLIC_KEY
                ),
                COMMAND_NAME
            )
        } catch (t: Throwable) {
            failWithMessage(log, t.message)
            return null
        }
        return args
    }

    private fun assertCorrectNumOfArgs(args: List<String>): List<String>? {
        if (args.size != 3) {
            log.err().println(strings.getf("General.invalidArgNum", COMMAND_NAME))
            return null
        }
        return args
    }

    private fun tryMergeInPlace(args: List<String>): List<String>? {
        try {
            if (args[0] == FIRST_FLAG || args[0] == SECOND_FLAG) {
                val logFiles = resolveFiles(args[1], args[2])
                mergeHelper.mergeToFirstOrSecond(logFiles, args[0] == FIRST_FLAG)
                return null
            }
        } catch (e: MendLockedException) {
            failWithMessage(log, strings.getf("Merge.mendLocked", Unlock.COMMAND_NAME))
            return null
        } catch (t: Throwable) {
            failWithMessage(log, t.message)
            return null
        }
        return args
    }

    private fun tryMergeToNew(args: List<String>): List<String>? {
        try {
            val logFiles = resolveFiles(args[0], args[1])
            mergeHelper.mergeLogFilesToNew(
                logFiles,
                File(fileResolveHelper.ensureLogNameHasFileExtension(args[2]))
            )
        } catch (e: MendLockedException) {
            failWithMessage(log, strings.getf("Merge.mendLocked", Unlock.COMMAND_NAME))
        } catch (t: Throwable) {
            failWithMessage(log, t.message)
        }
        return null
    }

    private fun resolveFiles(file1: String, file2: String): Pair<File, File> {
        val firstLog: File = fileResolveHelper.resolveAsLogFilePath(file1)
        val secondLog: File = fileResolveHelper.resolveAsLogFilePath(file2)
        fileResolveHelper.assertFileExistsAndHasExtension(
            file1,
            AppProperties.LOG_FILE_EXTENSION,
            firstLog
        )
        fileResolveHelper.assertFileExistsAndHasExtension(
            file2,
            AppProperties.LOG_FILE_EXTENSION,
            secondLog
        )
        return Pair(firstLog, secondLog)
    }

    public override fun execute(args: List<String>) {
        executeBehaviourChain(behaviourChain, args)
    }

    override val usageText: String
        get() = strings.getf("Merge.usage", COMMAND_NAME, FIRST_FLAG, SECOND_FLAG)
    override val descriptionText: String
        get() = strings["Merge.description"]
    override val commandAliases: List<String>
        get() = listOf(COMMAND_NAME)

    companion object {
        const val COMMAND_NAME = "merge"
        const val FIRST_FLAG = "-1"
        const val SECOND_FLAG = "-2"
    }
}