package co.samco.mend4.desktop.helper

import co.samco.mend4.core.Settings
import co.samco.mend4.core.exception.CorruptSettingsException
import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.dao.OSDao
import co.samco.mend4.desktop.output.PrintStreamProvider
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintStream
import java.util.Scanner
import javax.inject.Inject

class ShredHelper @Inject constructor(
    private val strings: I18N,
    private val settings: Settings,
    private val log: PrintStreamProvider,
    private val fileResolveHelper: FileResolveHelper,
    private val osDao: OSDao
) {

    fun generateShredCommandArgs(fileName: String, commandString: String): Array<String> {
        return commandString
            .replace(strings["Shred.fileName"], fileName)
            .split(" ")
            .dropLastWhile { it.isEmpty() }
            .toTypedArray()
    }

    fun tryShredFile(absolutePath: String) {
        val shredCommand = settings.getValue(Settings.Name.SHREDCOMMAND)
            ?: throw CorruptSettingsException(
                strings["Shred.noShredCommand"],
                Settings.Name.SHREDCOMMAND
            )

        val shredCommandArgs = generateShredCommandArgs(absolutePath, shredCommand)
        log.err().println(strings.getf("Shred.cleaning", absolutePath))

        val tr: Process = osDao.exec(shredCommandArgs)
        inheritIO(tr.errorStream, log.err())
        inheritIO(tr.inputStream, log.out())

        InputStreamReader(tr.inputStream).use { isr ->
            BufferedReader(isr).use { rd ->
                var line = rd.readLine()
                while (line != null) {
                    log.out().println(line)
                    line = rd.readLine()
                }
            }
        }
    }

    private fun inheritIO(src: InputStream, dest: PrintStream) {
        Thread {
            val sc = Scanner(src)
            while (sc.hasNextLine()) dest.println(sc.nextLine())
        }.apply { start() }
    }

    fun shredFilesInDirectory(dir: String) {
        val dirFile = File(dir)
        fileResolveHelper.assertDirectoryExists(dirFile)
        osDao.listFiles(dirFile)?.forEach { tryShredFile(it.absolutePath) }
    }
}