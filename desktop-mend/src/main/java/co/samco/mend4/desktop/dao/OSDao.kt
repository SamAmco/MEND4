package co.samco.mend4.desktop.dao

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.CopyOption
import java.nio.file.Path

interface OSDao {
    fun exists(file: File): Boolean

    fun isFile(file: File): Boolean

    fun isDirectory(file: File): Boolean

    fun canWrite(file: File): Boolean

    fun readPassword(hint: String): CharArray

    fun readLine(): String

    fun readAllBytes(file: File): ByteArray

    fun listFiles(file: File): Array<File>?

    fun fileOutputSteam(file: File, append: Boolean = false): OutputStream

    fun fileInputStream(file: File): InputStream

    fun createNewFile(file: File): Boolean

    fun desktopOpenFile(file: File)

    fun renameFile(source: File, target: File): Boolean

    fun move(source: Path, target: Path, vararg options: CopyOption)

    fun exec(args: Array<String>): Process

    fun getHomeDirectory(): String

    fun getSettingsFile(): File
}