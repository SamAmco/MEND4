package co.samco.mend4.desktop.helper

import co.samco.mend4.core.exception.CorruptSettingsException
import co.samco.mend4.desktop.exception.FileAlreadyExistsException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

interface FileResolveHelper {
    val mendDirFile: File
    val settingsFile: File
    val publicKeyFile: File
    val privateKeyFile: File

    @get:Throws(IOException::class, CorruptSettingsException::class)
    val currentLogFile: File

    fun isFile(file: File): Boolean

    fun fileExistsAndHasExtension(extension: String, file: File): Boolean

    @Throws(FileNotFoundException::class)
    fun assertFileExists(file: File)

    @Throws(FileAlreadyExistsException::class)
    fun assertFileDoesNotExist(file: File)

    @Throws(FileNotFoundException::class)
    fun assertFileExistsAndHasExtension(name: String, extension: String, file: File)

    @Throws(FileNotFoundException::class)
    fun assertDirectoryExists(file: File)

    @Throws(IllegalArgumentException::class)
    fun assertDirWritable(dir: String)

    @Throws(FileNotFoundException::class)
    fun resolveFile(identifier: String): File

    fun ensureLogNameHasFileExtension(logName: String): String

    fun resolveAsLogFilePath(filePath: String): File

    @Throws(CorruptSettingsException::class, IOException::class)
    fun resolveAsEncFilePath(fileIdentifier: String): File

    fun getTempFile(dir: String): File
}


