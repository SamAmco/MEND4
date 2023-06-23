package co.samco.mend4.desktop.dao.impl

import co.samco.mend4.desktop.dao.OSDao
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.Path

class OSDaoImpl : OSDao {
    override fun exists(file: File): Boolean = file.exists()

    override fun readPassword(hint: String): CharArray = System.console().readPassword(hint)

    override fun readLine(): String = System.console().readLine()

    override fun readAllBytes(file: File): ByteArray = Files.readAllBytes(file.toPath())

    override fun listFiles(file: File): Array<File>? = file.listFiles()

    override fun fileOutputSteam(file: File, append: Boolean): OutputStream =
        if (append) FileOutputStream(file, true)
        else FileOutputStream(file)

    override fun fileInputStream(file: File): InputStream = FileInputStream(file)

    override fun createNewFile(file: File): Boolean = file.createNewFile()

    override fun move(source: Path, target: Path, vararg options: CopyOption) {
        Files.move(source, target, *options)
    }

    override fun exec(args: Array<String>): Process = Runtime.getRuntime().exec(args)
}