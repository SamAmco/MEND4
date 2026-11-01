package co.samco.mend4.desktop.session

import co.samco.mend4.core.AppProperties
import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.desktop.helper.FileResolveHelper
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.StandardCopyOption
import java.nio.file.Files
import java.security.PrivateKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.regex.Pattern
import javax.inject.Inject

/** Merges encrypted log records using only the key already owned by the session. */
class SessionMerge @Inject constructor(
    private val fileResolveHelper: FileResolveHelper,
    private val cryptoProvider: CryptoProvider,
) {
    internal fun merge(key: PrivateKey, context: SessionCommandContext) {
        val args = context.request.arguments
        require(args.size == 3) { "Usage: merge <first log> <second log> <output log> | merge <-1|-2> <first log> <second log>" }
        val inPlace = args.first() in listOf("-1", "-2")
        require(inPlace || !args.first().startsWith("-")) { "Unknown merge option: ${args.first()}" }
        val names = if (inPlace) args.drop(1) else args.take(2)
        val inputs = names.map { name ->
            val candidate = File(context.request.workingDirectory, name)
            val file = if (File(name).isAbsolute) File(name)
                else if (candidate.isFile) candidate else fileResolveHelper.resolveAsLogFilePath(name)
            require(file.isFile && file.extension == AppProperties.LOG_FILE_EXTENSION) { "No such encrypted log: $name" }
            file
        }
        val destination = if (inPlace) inputs[if (args.first() == "-1") 0 else 1] else {
            val name = fileResolveHelper.ensureLogNameHasFileExtension(args[2])
            if (File(name).isAbsolute) File(name) else File(context.request.workingDirectory, name)
        }
        val output = if (inPlace) Files.createTempFile(destination.parentFile.toPath(), "mend-merge-", ".mend").toFile()
            else destination
        try {
            inputs[0].inputStream().use { first ->
                inputs[1].inputStream().use { second ->
                    output.outputStream().use { target ->
                        mergeLogs(key, first, second, target)
                    }
                }
            }
            if (inPlace) Files.move(output.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            context.output.stdout.write("Merged to file: ${destination.absolutePath}\n".toByteArray())
        } finally {
            if (inPlace) Files.deleteIfExists(output.toPath())
        }
    }

    private fun mergeLogs(
        privateKey: PrivateKey,
        firstLog: InputStream,
        secondLog: InputStream,
        outputStream: OutputStream
    ) {
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
            firstDate = SimpleDateFormat(AppProperties.LOG_DATE_FORMAT).parse(matcher.group())
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
