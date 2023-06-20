package co.samco.mend4.core

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.CopyOption
import java.nio.file.Path

interface OSDao {
    val userHome: String
    val stdIn: InputStream

    fun desktopOpenFile(file: File)
    fun mkdirs(file: File)

    fun createNewFile(file: File)

    fun getDirectoryListing(dirFile: File): Array<File>
    fun getAbsolutePath(file: File): String

    fun executeCommand(commandArgs: Array<String>): Process
    fun getBaseName(file: File): String
    fun fileExists(file: File): Boolean
    fun fileIsFile(file: File): Boolean

    fun getInputStreamForFile(file: File): InputStream

    fun getOutputStreamForFile(file: File): OutputStream

    fun getOutputStreamForFile(file: File, append: Boolean): OutputStream

    fun writeDataToFile(data: ByteArray, outputFile: File)
    fun getFileExtension(file: File): String
    fun getFileExtension(filePath: String): String
    fun readPassword(message: String): CharArray

    fun moveFile(source: Path, target: Path, vararg options: CopyOption): Path
    fun renameFile(file: File, newFile: File)

    fun readAllFileBytes(path: Path): ByteArray
}