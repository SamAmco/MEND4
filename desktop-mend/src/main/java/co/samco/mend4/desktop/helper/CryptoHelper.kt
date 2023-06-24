package co.samco.mend4.desktop.helper

import co.samco.mend4.core.AppProperties
import co.samco.mend4.core.Settings
import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.core.exception.CorruptSettingsException
import co.samco.mend4.core.util.LogUtils
import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.dao.OSDao
import co.samco.mend4.desktop.exception.MendLockedException
import co.samco.mend4.desktop.output.PrintStreamProvider
import org.apache.commons.io.FilenameUtils
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject

class CryptoHelper @Inject constructor(
    private val strings: I18N,
    private val log: PrintStreamProvider,
    private val fileResolveHelper: FileResolveHelper,
    private val settings: Settings,
    private val cryptoProvider: CryptoProvider,
    private val keyHelper: KeyHelper,
    private val versionHelper: VersionHelper,
    private val osDao: OSDao
) {

    fun encryptFile(file: File, name: String?) {
        val fileName = name ?: SimpleDateFormat(AppProperties.ENC_FILE_NAME_FORMAT).format(Date())
        val fileExtension: String = FilenameUtils.getExtension(file.absolutePath)
        val encLocation = settings.getValue(Settings.Name.ENCDIR)
        val outputFile = File(encLocation, fileName + "." + AppProperties.ENC_FILE_EXTENSION)
        fileResolveHelper.assertFileDoesNotExist(outputFile)
        osDao.fileInputStream(file).use { fis ->
            osDao.fileOutputSteam(outputFile).use { fos ->
                log.err().println(
                    strings.getf(
                        "CryptoHelper.encryptingFile",
                        outputFile.absolutePath
                    )
                )

                cryptoProvider.encryptEncStream(fis, fos, fileExtension)

                log.err().println(
                    strings.getf(
                        "CryptoHelper.encryptFileComplete",
                        fileName
                    )
                )
            }
        }
    }

    fun encryptTextToLog(text: CharArray?, dropHeader: Boolean) {
        if (text == null || text.isEmpty()) return

        val currentLogFile = fileResolveHelper.currentLogFile
        //Create the file if it does not exist
        osDao.createNewFile(currentLogFile)
        val logText = String(text).let {
            if (dropHeader) it
            else LogUtils.addHeaderToLogText(
                it,
                strings["Platform.header"],
                versionHelper.version,
                strings.newLine
            )
        }
        osDao.fileOutputSteam(currentLogFile, true).use { fos ->
            cryptoProvider.encryptLogStream(logText, fos)
            val dateFormat: DateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
            val date = Date()
            log.out().println(
                strings.getf(
                    "CryptoHelper.successfullyLogged",
                    dateFormat.format(date)
                )
            )
        }
    }

    fun decryptLog(file: File) {
        val privateKey = keyHelper.privateKey ?: throw MendLockedException()
        osDao.fileInputStream(file).use { fis ->
            cryptoProvider.decryptLogStream(privateKey, fis, log.out())
        }
    }

    fun decryptFile(file: File, silent: Boolean) {
        val privateKey = keyHelper.privateKey ?: throw MendLockedException()
        val decDir = settings.getValue(Settings.Name.DECDIR)
            ?: throw CorruptSettingsException(
                strings.getf("General.dirRequired"),
                Settings.Name.DECDIR
            )
        fileResolveHelper.assertDirWritable(decDir)

        var outputFile = File(decDir, FilenameUtils.removeExtension(file.name))
        fileResolveHelper.assertFileDoesNotExist(outputFile)
        var fileExtension: String
        log.err().println(strings.getf("CryptoHelper.decryptingFile", outputFile.absolutePath))
        osDao.fileInputStream(file).use { fis ->
            osDao.fileOutputSteam(outputFile).use { fos ->
                fileExtension = cryptoProvider.decryptEncStream(privateKey, fis, fos)
            }
        }
        if (fileExtension != "") {
            val newFileName = outputFile.name + "." + fileExtension
            val newOutputFile = File(outputFile.parent + File.separator + newFileName)
            fileResolveHelper.assertFileDoesNotExist(newOutputFile)
            osDao.renameFile(outputFile, newOutputFile)
            outputFile = newOutputFile
        }
        log.err().println(strings["CryptoHelper.decryptComplete"])
        if (!silent) osDao.desktopOpenFile(outputFile)
    }
}