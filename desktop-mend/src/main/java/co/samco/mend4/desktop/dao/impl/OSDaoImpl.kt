package co.samco.mend4.desktop.dao.impl

import co.samco.mend4.core.AppProperties
import co.samco.mend4.desktop.dao.OSDao
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import java.awt.Desktop
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject

class OSDaoImpl @Inject constructor() : OSDao {
    override fun exists(file: File): Boolean = file.exists()

    override fun isFile(file: File): Boolean = file.isFile

    override fun isDirectory(file: File): Boolean = file.isDirectory

    override fun canWrite(file: File): Boolean = file.canWrite()

    override fun readPassword(hint: String): CharArray = System.console().readPassword(hint)

    override fun readLine(): String = System.console().readLine()

    override fun readAllBytes(file: File): ByteArray = Files.readAllBytes(file.toPath())

    override fun listFiles(file: File): Array<File>? = file.listFiles()

    override fun fileOutputSteam(file: File, append: Boolean): OutputStream =
        if (append) FileOutputStream(file, true)
        else FileOutputStream(file)

    override fun fileInputStream(file: File): InputStream = FileInputStream(file)

    override fun createNewFile(file: File): Boolean = file.createNewFile()

    override fun renameFile(source: File, target: File): Boolean = source.renameTo(target)

    override fun desktopOpenFile(file: File) = Desktop.getDesktop().open(file)

    override fun move(source: Path, target: Path, vararg options: CopyOption) {
        Files.move(source, target, *options)
    }

    override fun exec(args: Array<String>): Process = Runtime.getRuntime().exec(args)

    override fun getHomeDirectory(): String = System.getProperty("user.home")

    override fun getSettingsFile(): File = FileUtils.getFile(
        getHomeDirectory(),
        AppProperties.CONFIG_DIR_NAME,
        AppProperties.SETTINGS_FILE_NAME
    )

}