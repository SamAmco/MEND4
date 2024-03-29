package co.samco.mend4.desktop.commands

import co.samco.mend4.core.Settings
import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.core.crypto.UnlockResult
import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.dao.OSDao
import co.samco.mend4.desktop.dao.SettingsDao
import co.samco.mend4.desktop.helper.FileResolveHelper
import co.samco.mend4.desktop.helper.SettingsHelper
import co.samco.mend4.desktop.helper.ShredHelper
import co.samco.mend4.desktop.output.PrintStreamProvider
import org.apache.commons.codec.binary.Base64
import java.util.function.Function
import javax.inject.Inject

class Unlock @Inject constructor(
    private val strings: I18N,
    private val settings: SettingsDao,
    private val settingsHelper: SettingsHelper,
    private val log: PrintStreamProvider,
    private val cryptoProvider: CryptoProvider,
    private val shredHelper: ShredHelper,
    private val fileResolveHelper: FileResolveHelper,
    private val osDao: OSDao
) : CommandBase() {

    private lateinit var password: CharArray
    private lateinit var unlockResult: UnlockResult.Success

    private val behaviourChain = listOf(
        Function { a: List<String> -> assertSettingsPresent(a) },
        Function { a: List<String> -> readPassword(a) },
        Function { a: List<String> -> checkPassword(a) },
        Function { a: List<String> -> shredExistingKeys(a) },
        Function { a: List<String> -> decryptAndWriteKeys(a) }
    )

    private fun assertSettingsPresent(args: List<String>): List<String>? {
        try {
            settingsHelper.assertRequiredSettingsExist(
                arrayOf(
                    SettingsDao.SHRED_COMMAND,
                    Settings.Name.ASYMMETRIC_CIPHER_NAME,
                    Settings.Name.ASYMMETRIC_CIPHER_TRANSFORM,
                    Settings.Name.ASYMMETRIC_KEY_SIZE,
                    Settings.Name.PW_KEY_FACTORY_ITERATIONS,
                    Settings.Name.PW_KEY_FACTORY_MEMORY_KB,
                    Settings.Name.PW_KEY_FACTORY_PARALLELISM,
                    Settings.Name.PW_KEY_FACTORY_SALT,
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

    private fun readPassword(args: List<String>): List<String>? {
        password = osDao.readPassword(strings["Unlock.enterPassword"]);
        return args
    }

    private fun checkPassword(args: List<String>): List<String>? {
        try {
            log.err().println()
            log.err().println(strings["Unlock.unlocking"])

            val unlockResult = cryptoProvider.unlock(password)

            if (unlockResult !is UnlockResult.Success) {
                log.err().println(strings["Unlock.incorrectPassword"])
                return null
            } else {
                this.unlockResult = unlockResult
            }
        } catch (t: Throwable) {
            failWithMessage(log, t.message)
        }
        return args
    }

    private fun shredExistingKeys(args: List<String>): List<String>? {
        try {
            if (osDao.exists(fileResolveHelper.privateKeyFile)) {
                shredHelper.tryShredFile(fileResolveHelper.privateKeyFile.absolutePath)
            }
            if (osDao.exists(fileResolveHelper.publicKeyFile)) {
                shredHelper.tryShredFile(fileResolveHelper.publicKeyFile.absolutePath)
            }
        } catch (t: Throwable) {
            failWithMessage(log, t.message)
        }
        return args
    }

    private fun decryptAndWriteKeys(args: List<String>): List<String>? {
        try {
            val privateKey = unlockResult.privateKey
            val publicKey = Base64.decodeBase64(settings.getValue(Settings.Name.PUBLIC_KEY))
            osDao.fileOutputSteam(fileResolveHelper.privateKeyFile).use { it.write(privateKey.encoded) }
            osDao.fileOutputSteam(fileResolveHelper.publicKeyFile).use { it.write(publicKey) }
            log.out().println(strings["Unlock.unlocked"])
        } catch (t: Throwable) {
            failWithMessage(log, t.message)
        }
        return args
    }

    public override fun execute(args: List<String>) {
        executeBehaviourChain(behaviourChain, args)
    }

    override val usageText: String
        get() = strings.getf("Unlock.usage", COMMAND_NAME)
    override val descriptionText: String
        get() = strings["Unlock.description"]
    override val commandAliases: List<String>
        get() = listOf(COMMAND_NAME)

    companion object {
        const val COMMAND_NAME = "unlock"
    }
}