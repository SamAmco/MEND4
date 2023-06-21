package co.samco.mend4.core

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

    const val PREFERRED_SYMMETRIC_TRANSFORM = "AES/CTR/NoPadding"
    const val PREFERRED_SYMMETRIC_CIPHER = "AES"
    const val PREFERRED_ASYMMETRIC_TRANSFORM = "RSA/ECB/PKCS1Padding"
    const val PREFERRED_ASYMMETRIC_CIPHER = "RSA"
    const val PREFERRED_ASYMMETRIC_KEY_SIZE = 4096
    const val PREFFERED_SYMMETRIC_KEY_SIZE = 256
}