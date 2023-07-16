package co.samco.mend4.desktop.commands

import co.samco.mend4.core.Settings
import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.core.exception.NoSuchSettingException
import co.samco.mend4.desktop.commands.Setup.Companion.DEFAULT_PW_FACTORY_ITERATIONS
import co.samco.mend4.desktop.commands.Setup.Companion.DEFAULT_PW_FACTORY_MEMORY_KB
import co.samco.mend4.desktop.commands.Setup.Companion.DEFAULT_PW_FACTORY_PARALLELISM
import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.dao.OSDao
import co.samco.mend4.desktop.dao.SettingsDao
import co.samco.mend4.desktop.helper.FileResolveHelper
import co.samco.mend4.desktop.output.PrintStreamProvider
import java.security.KeyPair
import java.util.function.Function
import javax.inject.Inject

class ChangePassword @Inject constructor(
    private val log: PrintStreamProvider,
    private val strings: I18N,
    private val cryptoProvider: CryptoProvider,
    private val fileResolveHelper: FileResolveHelper,
    private val settings: SettingsDao,
    private val osDao: OSDao
) : CommandBase() {

    companion object {
        const val COMMAND_NAME = "change-password"
    }

    private val behaviorChain: List<Function<List<String>, List<String>?>> = listOf(
        Function { readOldProperties(it) },
        Function { readKeyFiles(it) },
        Function { readNewPassword(it) },
        Function { readPasswordHashingParameters(it) },
        Function { storeKeysAndParams(it) },
    )

    private lateinit var keyPair: KeyPair
    private lateinit var password: CharArray
    private lateinit var pwKeyFactoryIterations: String
    private lateinit var pwKeyFactoryParallelism: String
    private lateinit var pwKeyFactoryMemoryKb: String

    private lateinit var oldSalt: String
    private lateinit var oldIv: String
    private lateinit var oldIterations: String
    private lateinit var oldParallelism: String
    private lateinit var oldMemoryKb: String
    private lateinit var oldEncryptedPrivateKey: String
    private lateinit var oldPublicKey: String

    private fun readOldProperties(args: List<String>): List<String>? {
        try {
            oldSalt = settings.getValue(Settings.Name.PW_KEY_FACTORY_SALT)
                ?: throw NoSuchSettingException(Settings.Name.PW_KEY_FACTORY_SALT)
            oldIv = settings.getValue(Settings.Name.PW_PRIVATE_KEY_CIPHER_IV)
                ?: throw NoSuchSettingException(Settings.Name.PW_PRIVATE_KEY_CIPHER_IV)
            oldIterations = settings.getValue(Settings.Name.PW_KEY_FACTORY_ITERATIONS)
                ?: throw NoSuchSettingException(Settings.Name.PW_KEY_FACTORY_ITERATIONS)
            oldParallelism = settings.getValue(Settings.Name.PW_KEY_FACTORY_PARALLELISM)
                ?: throw NoSuchSettingException(Settings.Name.PW_KEY_FACTORY_PARALLELISM)
            oldMemoryKb = settings.getValue(Settings.Name.PW_KEY_FACTORY_MEMORY_KB)
                ?: throw NoSuchSettingException(Settings.Name.PW_KEY_FACTORY_MEMORY_KB)
            oldEncryptedPrivateKey = settings.getValue(Settings.Name.ENCRYPTED_PRIVATE_KEY)
                ?: throw NoSuchSettingException(Settings.Name.ENCRYPTED_PRIVATE_KEY)
            oldPublicKey = settings.getValue(Settings.Name.PUBLIC_KEY)
                ?: throw NoSuchSettingException(Settings.Name.PUBLIC_KEY)
        } catch (e: NoSuchSettingException) {
            log.err().println(strings.getf("ChangePassword.missingSetting", e.missing.encodedName))
            return null
        }

        return args
    }

    private fun readKeyFiles(args: List<String>): List<String>? {
        if (osDao.exists(fileResolveHelper.publicKeyFile) && osDao.exists(fileResolveHelper.privateKeyFile)) {
            val publicKey = osDao.readAllBytes(fileResolveHelper.publicKeyFile)
            val privateKey = osDao.readAllBytes(fileResolveHelper.privateKeyFile)
            keyPair = cryptoProvider.getKeyPairFromBytes(privateKey, publicKey)
        } else {
            failWithMessage(log, strings.getf("ChangePassword.mendLocked", Unlock.COMMAND_NAME))
            return null
        }
        return args
    }

    private fun readNewPassword(args: List<String>): List<String>? {
        var readPassword: CharArray? = null
        while (readPassword == null) {
            val passArr1 = osDao.readPassword(strings["ChangePassword.enterPassword"])
            val passArr2 = osDao.readPassword(strings["ChangePassword.reEnterPassword"])
            if (passArr1.contentEquals(passArr2)) {
                readPassword = passArr1
            } else {
                log.err().println(strings["SetupMend.passwordMismatch"])
            }
        }
        password = readPassword
        log.out().println()
        return args
    }

    private fun readPasswordHashingParameters(args: List<String>): List<String>? {
        try {
            log.out().println(strings["SetupMend.argon2Hint"])
            log.out().println()

            log.out().println(
                strings.getf(
                    "SetupMend.pwKeyFactoryIterations",
                    DEFAULT_PW_FACTORY_ITERATIONS
                )
            )
            pwKeyFactoryIterations = readInt(DEFAULT_PW_FACTORY_ITERATIONS)
            log.out().println()

            log.out().println(
                strings.getf(
                    "SetupMend.pwKeyFactoryParallelism",
                    DEFAULT_PW_FACTORY_PARALLELISM
                )
            )
            pwKeyFactoryParallelism = readInt(DEFAULT_PW_FACTORY_PARALLELISM)
            log.out().println()

            log.out().println(
                strings.getf(
                    "SetupMend.pwKeyFactoryMemory",
                    DEFAULT_PW_FACTORY_MEMORY_KB
                )
            )
            pwKeyFactoryMemoryKb = readInt(DEFAULT_PW_FACTORY_MEMORY_KB)
            log.out().println()

        } catch (t: Throwable) {
            failWithMessage(log, t.message)
            return null
        }
        return args
    }

    private fun storeKeysAndParams(args: List<String>): List<String>? {
        try {
            settings.setValue(
                Settings.Name.PW_KEY_FACTORY_ITERATIONS,
                pwKeyFactoryIterations
            )
            settings.setValue(
                Settings.Name.PW_KEY_FACTORY_PARALLELISM,
                pwKeyFactoryParallelism
            )
            settings.setValue(
                Settings.Name.PW_KEY_FACTORY_MEMORY_KB,
                pwKeyFactoryMemoryKb
            )

            cryptoProvider.storeEncryptedKeys(password, keyPair)

            log.out().println(strings["ChangePassword.success"])
        } catch (t: Throwable) {
            settings.setValue(Settings.Name.PW_KEY_FACTORY_ITERATIONS, oldIterations)
            settings.setValue(Settings.Name.PW_KEY_FACTORY_PARALLELISM, oldParallelism)
            settings.setValue(Settings.Name.PW_KEY_FACTORY_MEMORY_KB, oldMemoryKb)

            settings.setValue(Settings.Name.PW_KEY_FACTORY_SALT, oldSalt)
            settings.setValue(Settings.Name.PW_PRIVATE_KEY_CIPHER_IV, oldIv)
            settings.setValue(Settings.Name.ENCRYPTED_PRIVATE_KEY, oldEncryptedPrivateKey)
            settings.setValue(Settings.Name.PUBLIC_KEY, oldPublicKey)

            failWithMessage(log, t.message)
            return null
        }
        return args
    }

    private fun readInt(default: Int): String {
        while (true) {
            val line = osDao.readLine().trim()
            if (line.isBlank()) return default.toString()
            if (line.matches(Regex("\\d+"))) return line
            log.err().println(strings["SetupMend.invalidNumber"])
        }
    }

    public override fun execute(args: List<String>) {
        executeBehaviourChain(behaviorChain, args)
    }

    override val usageText: String
        get() = strings.getf("ChangePassword.usage", COMMAND_NAME)
    override val descriptionText: String
        get() = strings["ChangePassword.description"]
    override val commandAliases: List<String>
        get() = listOf(COMMAND_NAME)
}