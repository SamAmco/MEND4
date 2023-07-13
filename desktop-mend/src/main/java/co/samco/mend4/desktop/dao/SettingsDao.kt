package co.samco.mend4.desktop.dao

import co.samco.mend4.core.Settings

interface SettingsDao : Settings {

    fun firstNonExistent(required: Array<Settings.Name>): Settings.Name?

    companion object {
        val CURRENT_LOG = Settings.Name("current-log")
        val LOG_DIR = Settings.Name("log-dir")
        val ENC_DIR = Settings.Name("enc-dir")
        val DEC_DIR = Settings.Name("dec-dir")
        val SHRED_COMMAND = Settings.Name("shred-command")

        val ALL_SETTINGS = arrayOf(
            CURRENT_LOG,
            LOG_DIR,
            ENC_DIR,
            DEC_DIR,
            SHRED_COMMAND,
            Settings.Name.ASYMMETRIC_CIPHER_NAME,
            Settings.Name.ASYMMETRIC_CIPHER_TRANSFORM,
            Settings.Name.ASYMMETRIC_KEY_SIZE,
            Settings.Name.PW_KEY_FACTORY_ITERATIONS,
            Settings.Name.PW_KEY_FACTORY_SALT,
            Settings.Name.PW_PRIVATE_KEY_CIPHER_IV,
            Settings.Name.ENCRYPTED_PRIVATE_KEY,
            Settings.Name.PUBLIC_KEY
        )
    }
}