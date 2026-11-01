package co.samco.mend4.desktop.session

import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.core.crypto.UnlockResult
import co.samco.mend4.desktop.dao.OSDao
import co.samco.mend4.desktop.helper.FileResolveHelper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class UnlockSession @Inject constructor(
    private val crypto: CryptoProvider,
    private val decrypt: SessionDecrypt,
    private val os: OSDao,
    private val files: FileResolveHelper,
) {
    fun run(args: List<String>) {
        require(args.isEmpty()) { "Usage: mend unlock" }
        require(System.getProperty("os.name").let { it == "Linux" || it == "Mac OS X" }) { "Sessions require Linux or macOS" }
        require(System.console() != null) { "mend unlock requires an interactive terminal" }
        require(!files.privateKeyFile.exists()) {
            "A legacy plaintext private key exists. Run mend lock to remove it before starting a session."
        }
        val password = os.readPassword("Password: ")
        val result = try { crypto.unlock(password) } finally { password.fill('\u0000') }
        require(result is UnlockResult.Success) { "Incorrect password" }
        val directory = Files.createTempDirectory(Path.of("/tmp"), "mend-",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        val locked = AtomicBoolean(false)
        var child: Process? = null
        val broker = SessionBroker(directory) { request, cwd, out, err ->
            check(!locked.get()) { "MEND is locked" }
            when (request.first()) {
                "dec" -> decrypt.decrypt(result.privateKey, request.drop(1), cwd, out, err)
                "lock" -> locked.set(true)
                else -> error("Unknown session command")
            }
            0
        }
        val cleanup = Thread {
            runCatching { broker.close() }
            child?.destroy()
            // Only the known files belonging to this session; never recursively remove a directory.
            listOf("session.sh", "bashrc", ".zshrc", ".zshenv").forEach { runCatching { Files.deleteIfExists(directory.resolve(it)) } }
            runCatching { Files.deleteIfExists(directory) }
        }
        Runtime.getRuntime().addShutdownHook(cleanup)
        try {
            broker.start()
            child = SessionShell.launch(directory, broker.socket, broker.credential)
            while (child.isAlive && !locked.get()) child.waitFor(100, java.util.concurrent.TimeUnit.MILLISECONDS)
        } finally {
            cleanup.run()
            Runtime.getRuntime().removeShutdownHook(cleanup)
            System.err.println("MEND locked.")
        }
    }
}
