package co.samco.mend4.desktop.commands

import co.samco.mend4.core.Settings
import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.dao.OSDao
import co.samco.mend4.desktop.dao.SettingsDao
import co.samco.mend4.desktop.helper.FileResolveHelper
import co.samco.mend4.desktop.output.PrintStreamProvider
import java.io.File
import java.io.IOException
import java.util.function.Function
import javax.inject.Inject

class Setup @Inject constructor(
    private val log: PrintStreamProvider,
    private val strings: I18N,
    private val cryptoProvider: CryptoProvider,
    private val fileResolveHelper: FileResolveHelper,
    private val settings: SettingsDao,
    private val osDao: OSDao
) : CommandBase() {

    companion object {
        const val COMMAND_NAME = "setup"
        const val FORCE_FLAG = "-f"

        const val DEFAULT_ASYMMETRIC_CIPHER = "X448"
        const val DEFAULT_ASYMMETRIC_CIPHER_TRANSFORM = "XIESWithSha256"
        const val DEFAULT_KEY_SIZE = 448
        const val DEFAULT_PW_FACTORY_ITERATIONS = 3
        const val DEFAULT_PW_FACTORY_PARALLELISM = 8
        const val DEFAULT_PW_FACTORY_MEMORY_KB = 256 * 1000

        const val DEFAULT_LOG_DIR_NAME = "log-dir"
        const val DEFAULT_ENC_DIR_NAME = "enc-dir"
        const val DEFAULT_DEC_DIR_NAME = "dec-dir"
        const val DEFAULT_SHRED_COMMAND = "rm -f <filename>"
        const val DEFAULT_LOG_FILE_NAME = "Log"
    }

    private var password: CharArray? = null

    private val behaviourChain: List<Function<List<String>, List<String>?>> = listOf(
        Function { a: List<String> -> checkAlreadySetup(a) },
        Function { a: List<String> -> checkArgNum(a) },
        Function { a: List<String> -> readPassword(a) },
        Function { a: List<String> -> ensureSettingsPathExists(a) },
        Function { a: List<String> -> setEncryptionProperties(a) },
        Function { a: List<String> -> setKeys(a) },
        Function { _: List<String> -> finishSetup() }
    )

    private fun checkAlreadySetup(args: List<String>): List<String>? {
        if (args.contains(FORCE_FLAG)) {
            return args.minus(FORCE_FLAG)
        } else if (osDao.exists(fileResolveHelper.settingsFile)) {
            log.err().println(
                strings.getf(
                    "SetupMend.alreadySetup",
                    fileResolveHelper.settingsFile,
                    FORCE_FLAG
                )
            )
            return null
        }
        return args
    }

    private fun checkArgNum(args: List<String>): List<String>? {
        if (args.isNotEmpty()) {
            log.err().println(strings.getf("General.invalidArgNum", COMMAND_NAME))
            return null
        }
        return args
    }

    private fun readPassword(args: List<String>): List<String> {
        while (password == null) {
            val passArr1 = osDao.readPassword(strings["SetupMend.enterPassword"])
            val passArr2 = osDao.readPassword(strings["SetupMend.reEnterPassword"])
            if (passArr1.contentEquals(passArr2)) {
                password = passArr1
            } else {
                log.err().println(strings["SetupMend.passwordMismatch"])
            }
        }
        log.out().println()
        return args
    }

    private fun ensureSettingsPathExists(args: List<String>): List<String> {
        log.out().println(strings.getf("SetupMend.creating", fileResolveHelper.mendDirFile))
        log.out().println()
        osDao.mkdirs(fileResolveHelper.mendDirFile)
        return args
    }

    private fun setEncryptionProperties(args: List<String>): List<String>? {
        try {
            log.out().println(strings["SetupMend.cipherHint"])
            log.out().println()

            log.out().println(
                strings.getf(
                    "SetupMend.asymmetricCipherName",
                    DEFAULT_ASYMMETRIC_CIPHER
                )
            )
            settings.setValue(
                Settings.Name.ASYMMETRIC_CIPHER_NAME,
                readString(DEFAULT_ASYMMETRIC_CIPHER)
            )

            log.out().println(
                strings.getf(
                    "SetupMend.asymmetricCipherTransform",
                    DEFAULT_ASYMMETRIC_CIPHER_TRANSFORM
                )
            )
            settings.setValue(
                Settings.Name.ASYMMETRIC_CIPHER_TRANSFORM,
                readString(DEFAULT_ASYMMETRIC_CIPHER_TRANSFORM)
            )

            log.out().println(
                strings.getf(
                    "SetupMend.asymmetricKeySize",
                    DEFAULT_KEY_SIZE.toString()
                )
            )
            settings.setValue(Settings.Name.ASYMMETRIC_KEY_SIZE, readInt(DEFAULT_KEY_SIZE))

            log.out().println(strings["SetupMend.argon2Hint"])
            log.out().println()

            log.out().println(
                strings.getf(
                    "SetupMend.pwKeyFactoryIterations",
                    DEFAULT_PW_FACTORY_ITERATIONS
                )
            )
            settings.setValue(
                Settings.Name.PW_KEY_FACTORY_ITERATIONS,
                readInt(DEFAULT_PW_FACTORY_ITERATIONS)
            )

            log.out().println(
                strings.getf(
                    "SetupMend.pwKeyFactoryParallelism",
                    DEFAULT_PW_FACTORY_PARALLELISM
                )
            )
            settings.setValue(
                Settings.Name.PW_KEY_FACTORY_PARALLELISM,
                readInt(DEFAULT_PW_FACTORY_PARALLELISM)
            )

            log.out().println(
                strings.getf(
                    "SetupMend.pwKeyFactoryMemory",
                    DEFAULT_PW_FACTORY_MEMORY_KB
                )
            )
            settings.setValue(
                Settings.Name.PW_KEY_FACTORY_MEMORY_KB,
                readInt(DEFAULT_PW_FACTORY_MEMORY_KB)
            )
        } catch (e: IOException) {
            //delete the settings file if we failed to setup for any reason
            // otherwise it will block subsequent setup attempts
            destroyConfigFile()
            failWithMessage(log, e.message)
            return null
        }
        return args
    }

    private fun readString(default: String): String {
        val line = osDao.readLine().trim()
        return line.ifBlank { return default }
    }

    private fun readInt(default: Int): String {
        while (true) {
            val line = osDao.readLine().trim()
            if (line.isBlank()) return default.toString()
            if (line.matches(Regex("\\d+"))) return line
            log.err().println(strings["SetupMend.invalidNumber"])
        }
    }

    private fun destroyConfigFile() {
        if (osDao.exists(fileResolveHelper.settingsFile)) {
            osDao.delete(fileResolveHelper.settingsFile)
        }
    }

    private fun setKeys(args: List<String>): List<String>? {
        try {
            log.out().println(strings["SetupMend.storingKeys"])
            log.out().println()

            val keys = cryptoProvider.generateKeyPair()
            cryptoProvider.storeEncryptedKeys(password!!, keys)
        } catch (t: Throwable) {
            //delete the settings file if we failed to setup for any reason
            // otherwise it will block subsequent setup attempts
            destroyConfigFile()
            failWithMessage(log, t.message)
            return null
        }
        return args
    }

    private fun finishSetup(): List<String>? {
        val logDir = File(fileResolveHelper.mendDirFile, DEFAULT_LOG_DIR_NAME)
        val encDir = File(fileResolveHelper.mendDirFile, DEFAULT_ENC_DIR_NAME)
        val decDir = File(fileResolveHelper.mendDirFile, DEFAULT_DEC_DIR_NAME)

        try {
            osDao.mkdirs(logDir)
            osDao.mkdirs(encDir)
            osDao.mkdirs(decDir)

            settings.setValue(SettingsDao.LOG_DIR, logDir.absolutePath)
            settings.setValue(SettingsDao.ENC_DIR, encDir.absolutePath)
            settings.setValue(SettingsDao.DEC_DIR, decDir.absolutePath)
            settings.setValue(SettingsDao.SHRED_COMMAND, DEFAULT_SHRED_COMMAND)
            settings.setValue(SettingsDao.CURRENT_LOG, DEFAULT_LOG_FILE_NAME)

        } catch (t: Throwable) {
            //delete the settings file if we failed to setup for any reason
            // otherwise it will block subsequent setup attempts
            destroyConfigFile()
            failWithMessage(log, t.message)
            return null
        }

        log.out().println(
            strings.getf(
                "SetupMend.yourLogDirIs",
                logDir.absolutePath,
                SettingsDao.LOG_DIR.encodedName
            )
        )
        log.out().println()
        log.out().println(
            strings.getf(
                "SetupMend.yourEncDirIs",
                encDir.absolutePath,
                SettingsDao.ENC_DIR.encodedName
            )
        )
        log.out().println()
        log.out().println(
            strings.getf(
                "SetupMend.yourDecDirIs",
                decDir.absolutePath,
                SettingsDao.DEC_DIR.encodedName
            )
        )
        log.out().println()
        log.out().println(strings.getf("SetupMend.yourShredCommandIs", DEFAULT_SHRED_COMMAND))
        log.out().println()
        log.out().println(
            strings.getf(
                "SetupMend.yourCurrentLogIs",
                DEFAULT_LOG_FILE_NAME,
                SettingsDao.CURRENT_LOG.encodedName
            )
        )

        log.out().println()
        log.out().println(strings["SetupMend.complete"])
        return null
    }

    public override fun execute(args: List<String>) {
        executeBehaviourChain(behaviourChain, args)
    }

    override val usageText: String
        get() = strings.getf("SetupMend.usage", COMMAND_NAME, FORCE_FLAG)
    override val descriptionText: String
        get() = strings["SetupMend.description"]
    override val commandAliases: List<String>
        get() = listOf(COMMAND_NAME)
}