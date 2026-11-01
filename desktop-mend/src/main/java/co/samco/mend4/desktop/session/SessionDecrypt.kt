package co.samco.mend4.desktop.session

import co.samco.mend4.core.AppProperties
import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.desktop.dao.OSDao
import co.samco.mend4.desktop.dao.SettingsDao
import co.samco.mend4.desktop.helper.FileResolveHelper
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.PrivateKey
import javax.inject.Inject

/** The broker supplies the key; no operation loads or exports a plaintext key file. */
class SessionDecrypt @Inject constructor(
    private val crypto: CryptoProvider,
    private val files: FileResolveHelper,
    private val settings: SettingsDao,
    private val os: OSDao,
) {
    fun decrypt(key: PrivateKey, args: List<String>, cwd: String, out: OutputStream, err: PrintStream) {
        require(args.isNotEmpty()) { "Usage: dec [--stdout] [-s] <file or log>" }
        val stdout = "--stdout" in args
        val silent = "-s" in args
        val names = args.filter { it != "--stdout" && it != "-s" }
        require(names.size == 1 && !names.single().startsWith("-")) { "Usage: dec [--stdout] [-s] <file or log>" }
        val name = names.single()
        val candidate = if (File(name).isAbsolute) File(name) else File(cwd, name)
        val file = if (candidate.isFile) candidate else {
            val enc = files.resolveAsEncFilePath(name)
            if (enc.isFile) enc else files.resolveAsLogFilePath(name)
        }
        require(file.isFile) { "No such encrypted file or log: $name" }
        when (file.extension) {
            AppProperties.LOG_FILE_EXTENSION -> file.inputStream().use {
                runBlocking { crypto.decryptLogStream(key, it, PrintStream(out, true)) }
            }
            AppProperties.ENC_FILE_EXTENSION -> if (stdout) {
                file.inputStream().use { runBlocking { crypto.decryptEncStream(key, it, out) } }
            } else openFile(key, file, silent, err)
            else -> error("Not a MEND encrypted file or log: $name")
        }
    }

    private fun openFile(key: PrivateKey, file: File, silent: Boolean, err: PrintStream) {
        val directory = File(requireNotNull(settings.getValue(SettingsDao.DEC_DIR)) { "dec-dir is not configured" })
        // Unique, owner-only plaintext files prevent overwrite and symlink races.
        var output = Files.createTempFile(directory.toPath(), "mend-", ".tmp",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        try {
            val extension = file.inputStream().use { input ->
                Files.newOutputStream(output).use { target ->
                    runBlocking { crypto.decryptEncStream(key, input, target) }
                }
            }
            require(extension.matches(Regex("[A-Za-z0-9_-]{0,32}"))) { "Unsafe file extension in encrypted file" }
            if (extension.isNotEmpty()) output = Files.move(output, output.resolveSibling("${output.fileName}.$extension"))
        } catch (e: Exception) {
            Files.deleteIfExists(output)
            throw e
        }
        err.println("Decrypted to: $output")
        if (!silent) os.desktopOpenFile(output.toFile())
    }
}
