package co.samco.mend4.desktop.commands

import co.samco.mend4.core.Settings
import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.dao.OSDao
import co.samco.mend4.desktop.dao.SettingsDao
import co.samco.mend4.desktop.helper.FileResolveHelper
import co.samco.mend4.desktop.output.PrintStreamProvider
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

    private var password: CharArray? = null

    private val behaviourChain: List<Function<List<String>, List<String>?>> = listOf(
        Function { a: List<String> -> checkAlreadySetup(a) },
        Function { a: List<String> -> checkArgNum(a) },
        Function { a: List<String> -> readPassword(a) },
        Function { a: List<String> -> ensureSettingsPathExists(a) },
        Function { a: List<String> -> setEncryptionProperties(a) },
        Function { a: List<String> -> setKeys(a) },
        Function { _: List<String> -> printSuccess() }
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
        if (args.size != 2 && args.isNotEmpty()) {
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
        return args
    }

    private fun ensureSettingsPathExists(args: List<String>): List<String> {
        log.out().println(strings.getf("SetupMend.creating", fileResolveHelper.mendDirFile))
        fileResolveHelper.mendDirFile.mkdirs()
        return args
    }

    private fun setEncryptionProperties(args: List<String>): List<String>? {
        try {
            log.out().println(strings["SetupMend.cipherHint"])

            val defaultAsymmetricCipher = "X448"
            log.out().println(
                strings.getf(
                    "SetupMend.asymmetricCipherName",
                    defaultAsymmetricCipher
                )
            )
            settings.setValue(
                Settings.Name.ASYMMETRIC_CIPHER_NAME,
                readString(defaultAsymmetricCipher)
            )

            val defaultAsymmetricCipherTransform = "XIES"
            log.out().println(
                strings.getf(
                    "SetupMend.asymmetricCipherTransform",
                    defaultAsymmetricCipherTransform
                )
            )
            settings.setValue(
                Settings.Name.ASYMMETRIC_CIPHER_TRANSFORM,
                readString(defaultAsymmetricCipherTransform)
            )

            val defaultKeySize = 448
            log.out().println(
                strings.getf(
                    "SetupMend.asymmetricKeySize",
                    defaultKeySize.toString()
                )
            )
            settings.setValue(Settings.Name.ASYMMETRIC_KEY_SIZE, readInt(defaultKeySize))

            log.out().println(strings["SetupMend.argon2Hint"])

            val defaultPwFactoryIterations = 2
            log.out().println(
                strings.getf(
                    "SetupMend.pwKeyFactoryIterations",
                    defaultPwFactoryIterations
                )
            )
            settings.setValue(
                Settings.Name.PW_KEY_FACTORY_ITERATIONS,
                readInt(defaultPwFactoryIterations)
            )

            val defaultPwFactoryParallelism = 8
            log.out().println(
                strings.getf(
                    "SetupMend.pwKeyFactoryParallelism",
                    defaultPwFactoryParallelism
                )
            )
            settings.setValue(
                Settings.Name.PW_KEY_FACTORY_PARALLELISM,
                readInt(defaultPwFactoryParallelism)
            )

            //1GB in KB
            val defaultPwFactoryMemory = 1048576
            log.out().println(
                strings.getf(
                    "SetupMend.pwKeyFactoryMemory",
                    defaultPwFactoryMemory
                )
            )
            settings.setValue(
                Settings.Name.PW_KEY_FACTORY_MEMORY_KB,
                readInt(defaultPwFactoryMemory)
            )
        } catch (e: IOException) {
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

    private fun setKeys(args: List<String>): List<String>? {
        try {
            val keys = if (args.size == 2) {
                val privateKey = osDao.readAllBytes(fileResolveHelper.resolveFile(args[0]))
                val publicKey = osDao.readAllBytes(fileResolveHelper.resolveFile(args[1]))
                cryptoProvider.getKeyPairFromBytes(privateKey, publicKey)
            } else {
                cryptoProvider.generateKeyPair()
            }
            cryptoProvider.storeEncryptedKeys(password!!, keys)
        } catch (t: Throwable) {
            failWithMessage(log, t.message)
            return null
        }
        return args
    }

    private fun printSuccess(): List<String>? {
        log.err().println(strings["SetupMend.complete"])
        return null
    }

    public override fun execute(args: List<String>) {
        executeBehaviourChain(behaviourChain, args)
    }

    override val usageText: String
        get() = strings.getf("SetupMend.usage", COMMAND_NAME)
    override val descriptionText: String
        get() = strings["SetupMend.description"]
    override val commandAliases: List<String>
        get() = listOf(COMMAND_NAME)

    companion object {
        const val COMMAND_NAME = "setup"
        const val FORCE_FLAG = "-f"
    }
}