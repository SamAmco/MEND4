package co.samco.mend4.core.bean

data class EncodedKeyInfo(
    val privateKey: String,
    val publicKey: String,
    val keySize: Int
)