package co.samco.mend4.desktop.helper.impl

import co.samco.mend4.core.exception.CorruptSettingsException
import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.dao.OSDao
import co.samco.mend4.desktop.dao.SettingsDao
import co.samco.mend4.desktop.helper.FileResolveHelper
import co.samco.mend4.desktop.helper.ShredHelper
import co.samco.mend4.desktop.output.PrintStreamProvider
import java.io.File
import java.io.InputStream
import java.io.PrintStream
import javax.inject.Inject

class ShredHelperImpl @Inject constructor(
    private val strings: I18N,
    private val settings: SettingsDao,
    private val log: PrintStreamProvider,
    private val fileResolveHelper: FileResolveHelper,
    private val osDao: OSDao
) : ShredHelper {

    override fun generateShredCommandArgs(fileName: String, commandString: String): Array<String> {
        return commandString
            .replace(strings["Shred.fileName"], fileName)
            .split(" ")
            .dropLastWhile { it.isEmpty() }
            .toTypedArray()
    }

    override fun tryShredFile(absolutePath: String) {
        val shredCommand = settings.getValue(SettingsDao.SHRED_COMMAND)
            ?: throw CorruptSettingsException(
                strings.getf(
                    "Shred.noShredCommand",
                    SettingsDao.SHRED_COMMAND.encodedName
                )
            )

        val shredCommandArgs = generateShredCommandArgs(absolutePath, shredCommand)
        log.err().println(strings.getf("Shred.cleaning", absolutePath))

        val tr: Process = osDao.exec(shredCommandArgs)
        val errors = inheritIO(tr.errorStream, log.err())
        val output = inheritIO(tr.inputStream, log.out())
        val status = tr.waitFor()
        errors.join()
        output.join()
        check(status == 0) { "Cleaning failed for $absolutePath (exit $status)" }
    }

    override fun shredFilesInDirectory(dir: String) {
        val dirFile = File(dir)
        fileResolveHelper.assertDirectoryExists(dirFile)
        osDao.listFiles(dirFile)?.forEach { tryShredFile(it.absolutePath) }
    }

    private fun inheritIO(src: InputStream, dest: PrintStream): Thread =
        Thread {
            src.bufferedReader().useLines { lines -> lines.forEach(dest::println) }
        }.apply { start() }
}
