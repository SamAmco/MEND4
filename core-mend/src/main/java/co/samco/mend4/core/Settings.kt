package co.samco.mend4.core

import co.samco.mend4.core.exception.CorruptSettingsException
import java.io.IOException

interface Settings {
    @Throws(IOException::class)
    fun setValue(name: Name, value: String)

    @Throws(IOException::class)
    fun valueSet(name: Name): Boolean

    @Throws(IOException::class, CorruptSettingsException::class)
    fun getValue(name: Name): String?

    enum class Name(val encodedName: String) {
        SYMMETRIC_CIPHER_NAME("symmetric-cipher-name"),
        SYMMETRIC_CIPHER_TRANSFORM("symmetric-cipher-transform"),
        SYMMETRIC_KEY_SIZE("symmetric-key-size"),
        ASYMMETRIC_CIPHER_NAME("asymmetric-cipher-name"),
        ASYMMETRIC_CIPHER_TRANSFORM("asymmetric-cipher-transform"),
        ASYMMETRIC_KEY_SIZE("asymmetric-key-size"),
        PW_KEY_FACTORY_ALGORITHM("pw-key-factory-algorithm"),
        PW_KEY_FACTORY_ITERATIONS("pw-key-factory-iterations"),
        PW_KEY_FACTORY_SALT("pw-key-factory-salt"),
        PW_PRIVATE_KEY_CIPHER_IV("pw-private-key-cipher-iv"),
        ENCRYPTED_PRIVATE_KEY("encrypted-private-key"),
        PUBLIC_KEY("public-key"),

        //TODO these should be moved to desktop specific settings
        CURRENTLOG("currentlog"),
        LOGDIR("logdir"),
        ENCDIR("encdir"),
        DECDIR("decdir"),
        SHREDCOMMAND("shredcommand");

        override fun toString(): String {
            return encodedName
        }
    }
}