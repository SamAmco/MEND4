package co.samco.mend4.core

import javax.crypto.spec.IvParameterSpec

object AppProperties {
    const val CONFIG_DIR_NAME = ".MEND4"
    const val SETTINGS_FILE_NAME = "Settings.xml"
    const val PRIVATE_KEY_FILE_NAME = "prKey"
    const val PUBLIC_KEY_FILE_NAME = "pubKey"
    const val LOG_FILE_EXTENSION = "mend"
    const val ENC_FILE_EXTENSION = "enc"
    const val LOG_DATE_FORMAT = "dd/MM/yyyy HH:mm:ss"
    const val LOG_HEADER =
        "//MEND%s//%s////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////"
    const val DEFAULT_LOG_FILE_NAME = "Log"
    const val ENC_FILE_NAME_FORMAT = "yyyyMMddHHmmssSSS"
    const val PREFERRED_AES_ALG = "AES/CTR/NoPadding"
    const val PREFERRED_RSA_ALG = "RSA/ECB/PKCS1Padding"
    const val PREFERRED_RSA_KEY_SIZE = 4096
    const val PREFERRED_AES_KEY_SIZE = 256
    const val AES_KEY_GEN_ITERATIONS = 65536

    //TODO get rid of these they should be generated
    @JvmField
    val PASSCHECK_SALT = byteArrayOf(
        0xd7.toByte(),
        0x73.toByte(),
        0x31.toByte(),
        0x8a.toByte(),
        0x2e.toByte(),
        0xc8.toByte(),
        0xef.toByte(),
        0x99.toByte()
    )
    @JvmField
    val STANDARD_IV = IvParameterSpec(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
}