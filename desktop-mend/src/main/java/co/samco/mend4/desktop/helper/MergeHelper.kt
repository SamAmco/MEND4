package co.samco.mend4.desktop.helper

import co.samco.mend4.core.Settings
import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.dao.OSDao
import co.samco.mend4.desktop.exception.MendLockedException
import co.samco.mend4.desktop.output.PrintStreamProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.PrivateKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.regex.Pattern
import javax.inject.Inject

class MergeHelper @Inject constructor(
    private val log: PrintStreamProvider,
    private val strings: I18N,
    private val fileResolveHelper: FileResolveHelper,
    private val cryptoProvider: CryptoProvider,
    private val keyHelper: KeyHelper,
    private val settings: Settings,
    private val osDao: OSDao
) {

    fun mergeToFirstOrSecond(logFiles: Pair<File, File>, first: Boolean) {
        val firstLog = logFiles.first
        val secondLog = logFiles.second
        val logDir = settings.getValue(Settings.Name.LOGDIR)
        val tempFile = fileResolveHelper.getTempFile(logDir!!)
        mergeLogFilesToNew(logFiles, tempFile)
        if (first) {
            osDao.move(
                tempFile.toPath(),
                firstLog.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
            log.out().println(
                strings.getf(
                    "MergeHelper.movedFile",
                    tempFile.absolutePath,
                    firstLog.absolutePath
                )
            )
        } else {
            osDao.move(
                tempFile.toPath(),
                secondLog.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
            log.out().println(
                strings.getf(
                    "MergeHelper.movedFile",
                    tempFile.absolutePath,
                    secondLog.absolutePath
                )
            )
        }
    }

    fun mergeLogFilesToNew(files: Pair<File, File>, outputLog: File) {
        osDao.fileInputStream(files.first).use { f1InputStream ->
            osDao.fileInputStream(files.second).use { f2InputStream ->
                osDao.fileOutputSteam(outputLog).use { fOutputStream ->
                    outputLog.createNewFile()
                    mergeLogs(f1InputStream, f2InputStream, fOutputStream)
                    log.out().println(
                        strings.getf(
                            "MergeHelper.mergeComplete",
                            outputLog.absolutePath
                        )
                    )
                }
            }
        }
    }

    private fun mergeLogs(
        firstLog: InputStream,
        secondLog: InputStream,
        outputStream: OutputStream
    ) {
        val privateKey = keyHelper.privateKey ?: throw MendLockedException()
        var firstLogEntry = parseNextLog(firstLog, privateKey)
        var secondLogEntry = parseNextLog(secondLog, privateKey)
        var lastLogEntry: LogEntry? = null
        while (firstLogEntry != null || secondLogEntry != null) {
            if (firstLogEntry != null && firstLogEntry.before(secondLogEntry)) {
                writeIfNotDuplicate(firstLogEntry, lastLogEntry, outputStream)
                lastLogEntry = firstLogEntry
                firstLogEntry = parseNextLog(firstLog, privateKey)
            } else {
                writeIfNotDuplicate(secondLogEntry, lastLogEntry, outputStream)
                lastLogEntry = secondLogEntry
                secondLogEntry = parseNextLog(secondLog, privateKey)
            }
        }
    }

    private fun writeIfNotDuplicate(
        nextEntry: LogEntry?,
        lastEntry: LogEntry?,
        outputStream: OutputStream
    ) {
        if (lastEntry == null || nextEntry != lastEntry) {
            outputStream.write(nextEntry!!.data)
        }
    }

    private fun parseNextLog(inputStream: InputStream, privateKey: PrivateKey): LogEntry? {
        val lc1Bytes = ByteArray(4)
        return if (cryptoProvider.logHasNext(inputStream, lc1Bytes)) {
            parseNextLog(inputStream, lc1Bytes, privateKey)
        } else {
            null
        }
    }

    private fun parseNextLog(
        inputStream: InputStream,
        lc1Bytes: ByteArray,
        privateKey: PrivateKey
    ): LogEntry {
        val (logDataBlocks, logText) = cryptoProvider
            .getNextLogTextWithDataBlocks(inputStream, privateKey, lc1Bytes)
        var firstDate: Date? = null
        val pattern = Pattern.compile("(\\d+)/(\\d+)/(\\d+) (\\d+):(\\d+):(\\d+)")
        val matcher = pattern.matcher(logText)
        if (matcher.lookingAt()) {
            firstDate = SimpleDateFormat("dd/MM/yyyy HH:mm:ss").parse(matcher.group())
        }
        return LogEntry(logDataBlocks.asOneBlock, firstDate)
    }

    private class LogEntry(val data: ByteArray, private val dateTime: Date?) {
        override fun equals(other: Any?): Boolean {
            val otherL = other as LogEntry?
            return if (dateTime == null || otherL?.dateTime == null) {
                false
            } else dateTime == otherL.dateTime
        }

        override fun hashCode(): Int {
            return dateTime.hashCode()
        }

        fun before(other: LogEntry?): Boolean {
            return if (other == null) {
                true
            } else if (dateTime == null) {
                true
            } else if (other.dateTime == null) {
                false
            } else dateTime.before(other.dateTime)
        }
    }
}