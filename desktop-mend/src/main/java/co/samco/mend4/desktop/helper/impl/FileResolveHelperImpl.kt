package co.samco.mend4.desktop.helper.impl

import co.samco.mend4.core.AppProperties
import co.samco.mend4.core.exception.CorruptSettingsException
import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.dao.OSDao
import co.samco.mend4.desktop.dao.SettingsDao
import co.samco.mend4.desktop.exception.FileAlreadyExistsException
import co.samco.mend4.desktop.helper.FileResolveHelper
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject

class FileResolveHelperImpl @Inject constructor(
    private val settings: SettingsDao,
    private val strings: I18N,
    private val osDao: OSDao
) : FileResolveHelper {
    override val mendDirFile: File
        get() = File(osDao.getHomeDirectory(), AppProperties.CONFIG_DIR_NAME)

    override val settingsFile: File
        get() = osDao.getSettingsFile()

    override val publicKeyFile: File
        get() = File(mendDirFile, AppProperties.PUBLIC_KEY_FILE_NAME)

    override val privateKeyFile: File
        get() = File(mendDirFile, AppProperties.PRIVATE_KEY_FILE_NAME)

    override fun isFile(file: File): Boolean {
        return osDao.exists(file) && osDao.isFile(file)
    }

    override fun fileExistsAndHasExtension(extension: String, file: File): Boolean {
        return isFile(file) && file.extension == extension
    }

    @Throws(FileNotFoundException::class)
    override fun assertFileExists(file: File) {
        if (!isFile(file)) {
            throw FileNotFoundException(strings.getf("General.unknownIdentifier", file.name))
        }
    }

    @Throws(FileAlreadyExistsException::class)
    override fun assertFileDoesNotExist(file: File) {
        if (isFile(file)) throw FileAlreadyExistsException(
            strings.getf("General.fileAlreadyExists", file.absolutePath)
        )
    }

    @Throws(FileNotFoundException::class)
    override fun assertFileExistsAndHasExtension(name: String, extension: String, file: File) {
        if (!fileExistsAndHasExtension(extension, file)) {
            throw FileNotFoundException(strings.getf("General.unknownIdentifier", name))
        }
    }

    @Throws(FileNotFoundException::class)
    override fun assertDirectoryExists(file: File) {
        if (!osDao.exists(file) || !osDao.isDirectory(file) || osDao.listFiles(file) == null) {
            throw FileNotFoundException(strings.getf("General.dirNotFound ", file.absolutePath));
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun assertDirWritable(dir: String) {
        val file = File(dir)
        require(osDao.isDirectory(file) && osDao.exists(file) && osDao.canWrite(file)) {
            strings.getf("General.couldNotFindOrWriteDir", file.absolutePath)
        }
    }

    @Throws(FileNotFoundException::class)
    override fun resolveFile(identifier: String): File {
        return File(identifier).also { assertFileExists(it) }
    }

    override fun ensureLogNameHasFileExtension(logName: String): String {
        if (!FilenameUtils.getExtension(logName).equals(AppProperties.LOG_FILE_EXTENSION)) {
            return logName + "." + AppProperties.LOG_FILE_EXTENSION
        }
        return logName
    }

    @get:Throws(IOException::class, CorruptSettingsException::class)
    override val currentLogFile: File
        get() {
            val storedCurrentLog = settings.getValue(SettingsDao.CURRENT_LOG)
                ?: throw CorruptSettingsException(
                    strings.getf(
                        "General.currentLogNotSet",
                        SettingsDao.CURRENT_LOG.encodedName
                    )
                )

            val currentLog = ensureLogNameHasFileExtension(storedCurrentLog)
            return FileUtils.getFile(settings.getValue(SettingsDao.LOG_DIR), currentLog)
        }

    private fun filePathIsEncId(filePath: String): Boolean {
        return filePath.matches(Regex("\\d{14}"))
                || filePath.matches(Regex("\\d{16}"))
                || filePath.matches(Regex("\\d{17}"))
    }

    override fun resolveAsLogFilePath(filePath: String): File {
        return when {
            FilenameUtils.getExtension(filePath).equals("") -> FileUtils.getFile(
                settings.getValue(SettingsDao.LOG_DIR),
                filePath + "." + AppProperties.LOG_FILE_EXTENSION
            )

            File(filePath).exists() -> File(filePath)

            settings.getValue(SettingsDao.LOG_DIR) != null -> FileUtils.getFile(
                settings.getValue(SettingsDao.LOG_DIR),
                filePath
            )

            else -> throw FileNotFoundException(strings.getf("General.unknownIdentifier", filePath))
        }
    }

    @Throws(CorruptSettingsException::class, IOException::class)
    override fun resolveAsEncFilePath(fileIdentifier: String): File {
        return if (filePathIsEncId(fileIdentifier)) {
            FileUtils.getFile(
                settings.getValue(SettingsDao.ENC_DIR),
                fileIdentifier + "." + AppProperties.ENC_FILE_EXTENSION
            )
        } else {
            File(fileIdentifier)
        }
    }

    override fun getTempFile(dir: String): File {
        val tempName = "tmp"
        var tempSuffix = 0
        var currentOutFile: File
        do {
            currentOutFile =
                File(dir, tempName + tempSuffix + "." + AppProperties.LOG_FILE_EXTENSION)
            tempSuffix++
        } while (osDao.exists(currentOutFile))
        return currentOutFile
    }
}