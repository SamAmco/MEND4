package co.samco.mend4.desktop.helper

import co.samco.mend4.core.AppProperties
import co.samco.mend4.core.Settings
import co.samco.mend4.core.exception.CorruptSettingsException
import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.exception.FileAlreadyExistsException
import dagger.Lazy
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject

class FileResolveHelper @Inject constructor(
    private val settings: Lazy<Settings>,
    private val strings: I18N
) {

    fun isFile(file: File): Boolean {
        return file.exists() && file.isFile
    }

    fun fileExistsAndHasExtension(extension: String, file: File): Boolean {
        return isFile(file) && file.extension == extension
    }

    @Throws(FileNotFoundException::class)
    fun assertFileExists(file: File) {
        if (!isFile(file)) {
            throw FileNotFoundException(strings.getf("General.unknownIdentifier", file.name))
        }
    }

    @Throws(FileAlreadyExistsException::class)
    fun assertFileDoesNotExist(file: File) {
        if (isFile(file)) throw FileAlreadyExistsException(
            strings.getf("General.fileAlreadyExists", file.absolutePath)
        )
    }

    @Throws(FileNotFoundException::class)
    fun assertFileExistsAndHasExtension(name: String, extension: String, file: File) {
        if (!fileExistsAndHasExtension(extension, file)) {
            throw FileNotFoundException(strings.getf("General.unknownIdentifier", name))
        }
    }

    @Throws(FileNotFoundException::class)
    fun assertDirectoryExists(file: File) {
        if (!file.exists() || !file.isDirectory || file.listFiles() == null) {
            throw FileNotFoundException(strings.getf("General.dirNotFound ", file.absolutePath));
        }
    }

    @Throws(IllegalArgumentException::class)
    fun assertDirWritable(dir: String) {
        val file = File(dir)
        require(file.isDirectory && file.exists() && file.canWrite()) {
            strings.getf("General.couldNotFindOrWriteDir", file.absolutePath)
        }
    }

    @Throws(FileNotFoundException::class)
    fun resolveFile(identifier: String): File {
        val file = File(identifier)
        assertFileExists(file)
        return file
    }

    val mendDirFile: File
        get() = File(System.getProperty("user.home"), AppProperties.CONFIG_DIR_NAME)

    val settingsFile: File
        get() = File(mendDirFile, AppProperties.SETTINGS_FILE_NAME)

    val publicKeyFile: File
        get() = File(mendDirFile, AppProperties.PUBLIC_KEY_FILE_NAME)

    val privateKeyFile: File
        get() = File(mendDirFile, AppProperties.PRIVATE_KEY_FILE_NAME)

    fun ensureLogNameHasFileExtension(logName: String): String {
        if (!FilenameUtils.getExtension(logName).equals(AppProperties.LOG_FILE_EXTENSION)) {
            return logName + "." + AppProperties.LOG_FILE_EXTENSION
        }
        return logName
    }

    @get:Throws(IOException::class, CorruptSettingsException::class)
    val currentLogFile: File
        get() {
            val storedCurrentLog = settings.get().getValue(Settings.Name.CURRENTLOG)
                ?: throw CorruptSettingsException(
                    strings["General.dirRequired"],
                    Settings.Name.CURRENTLOG
                )

            val currentLog = ensureLogNameHasFileExtension(storedCurrentLog)
            return FileUtils.getFile(settings.get().getValue(Settings.Name.LOGDIR), currentLog)
        }

    private fun filePathIsEncId(filePath: String): Boolean {
        return filePath.matches(Regex("\\d{14}"))
                || filePath.matches(Regex("\\d{16}"))
                || filePath.matches(Regex("\\d{17}"))
    }

    @Throws(CorruptSettingsException::class, IOException::class)
    fun resolveAsLogFilePath(filePath: String): File {
        return when {
            FilenameUtils.getExtension(filePath).equals("") -> FileUtils.getFile(
                settings.get().getValue(Settings.Name.LOGDIR),
                filePath + "." + AppProperties.LOG_FILE_EXTENSION
            )

            File(filePath).exists() -> File(filePath)

            else -> FileUtils.getFile(
                settings.get().getValue(Settings.Name.LOGDIR),
                ensureLogNameHasFileExtension(filePath)
            )

        }
    }

    @Throws(CorruptSettingsException::class, IOException::class)
    fun resolveAsEncFilePath(fileIdentifier: String): File {
        return if (filePathIsEncId(fileIdentifier)) {
            FileUtils.getFile(
                settings.get().getValue(Settings.Name.ENCDIR),
                fileIdentifier + "." + AppProperties.ENC_FILE_EXTENSION
            )
        } else {
            File(fileIdentifier)
        }
    }

    fun getTempFile(dir: String): File {
        val tempName = "tmp"
        var tempSuffix = 0
        var currentOutFile: File
        do {
            currentOutFile =
                File(dir, tempName + tempSuffix + "." + AppProperties.LOG_FILE_EXTENSION)
            tempSuffix++
        } while (currentOutFile.exists())
        return currentOutFile
    }
}