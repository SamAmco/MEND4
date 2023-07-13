package co.samco.mend4.core

interface Settings {
    fun setValue(name: Name, value: String)

    fun getValue(name: Name): String?

    data class Name(val encodedName: String) {
        companion object {
            val ASYMMETRIC_CIPHER_NAME = Name("asymmetric-cipher-name")
            val ASYMMETRIC_CIPHER_TRANSFORM = Name("asymmetric-cipher-transform")
            val ASYMMETRIC_KEY_SIZE = Name("asymmetric-key-size")
            val PW_KEY_FACTORY_ITERATIONS = Name("pw-key-factory-iterations")
            val PW_KEY_FACTORY_PARALLELISM = Name("pw-key-factory-parallelism")
            val PW_KEY_FACTORY_SALT = Name("pw-key-factory-salt")
            val PW_KEY_FACTORY_MEMORY_KB = Name("pw-key-factory-memory-kb")
            val PW_PRIVATE_KEY_CIPHER_IV = Name("pw-private-key-cipher-iv")
            val ENCRYPTED_PRIVATE_KEY = Name("encrypted-private-key")
            val PUBLIC_KEY = Name("public-key")
        }

        override fun toString(): String {
            return encodedName
        }
    }
}