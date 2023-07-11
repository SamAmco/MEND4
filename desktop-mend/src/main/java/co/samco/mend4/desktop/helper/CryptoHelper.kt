package co.samco.mend4.desktop.helper

import java.io.File

interface CryptoHelper {
    fun encryptFile(file: File, name: String?)
    fun encryptTextToLog(text: CharArray?, dropHeader: Boolean)
    fun decryptLog(file: File)
    fun decryptFile(file: File, decDirPath: String, silent: Boolean)
}

