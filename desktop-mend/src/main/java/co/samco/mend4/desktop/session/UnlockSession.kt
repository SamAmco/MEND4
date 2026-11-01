package co.samco.mend4.desktop.session

import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.core.crypto.UnlockResult
import co.samco.mend4.desktop.dao.OSDao
import co.samco.mend4.desktop.dao.SettingsDao
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
    private val settings: SettingsDao,
) {
    fun run(args: List<String>) {
        val timeout = SessionTimeout(SessionTimeout.parse(args, settings.getValue(SettingsDao.SESSION_TIMEOUT)))
        require(System.getProperty("os.name").let { it == "Linux" || it == "Mac OS X" }) { "Sessions require Linux or macOS" }
        require(System.console() != null) { "mend unlock requires an interactive terminal" }
        require(!files.privateKeyFile.exists()) {
            "A legacy plaintext private key exists. Run mend lock to remove it before starting a session."
        }
        val shellName = Path.of(System.getenv("SHELL") ?: "/bin/bash").fileName.toString()
        require(shellName in listOf("bash", "zsh")) { "MEND sessions support Bash and Zsh; set SHELL accordingly." }
        val password = os.readPassword("Password: ")
        val result = try { crypto.unlock(password) } finally { password.fill('\u0000') }
        require(result is UnlockResult.Success) { "Incorrect password" }
        val directory = Files.createTempDirectory(Path.of("/tmp"), "mend-",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        val locked = AtomicBoolean(false)
        var child: Process? = null
        val broker = try {
            SessionBroker(directory) { request, cwd, out, err ->
                check(!locked.get()) { "MEND is locked" }
                timeout.touch()
                when (request.first()) {
                    "dec" -> decrypt.decrypt(result.privateKey, request.drop(1), cwd, out, err)
                    "lock" -> locked.set(true)
                    "touch" -> Unit
                    else -> error("Unknown session command")
                }
                0
            }
        } catch (e: Exception) {
            Files.deleteIfExists(directory.resolve("socket"))
            Files.deleteIfExists(directory)
            throw e
        }
        val cleaned = AtomicBoolean(false)
        fun cleanup() {
            if (!cleaned.compareAndSet(false, true)) return
            runCatching { broker.close() }
            child?.let {
                it.destroy()
                if (!it.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)) it.destroyForcibly()
            }
            // Only the known files belonging to this session; never recursively remove a directory.
            listOf("session.sh", "bashrc", ".zshrc", ".zshenv").forEach { runCatching { Files.deleteIfExists(directory.resolve(it)) } }
            runCatching { Files.deleteIfExists(directory) }
        }
        val shutdownHook = Thread(::cleanup, "mend-session-cleanup")
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        try {
            broker.start()
            child = SessionShell.launch(directory, broker.socket, broker.credential)
            timeout.touch()
            while (child.isAlive && !locked.get()) {
                when (timeout.poll()) {
                    SessionTimeout.Event.WARN -> System.err.println("\nMEND will lock shortly. Press Enter at the shell prompt to remain unlocked.")
                    SessionTimeout.Event.EXPIRED -> {
                        System.err.println("\nMEND session timed out.")
                        locked.set(true)
                    }
                    SessionTimeout.Event.NONE -> Unit
                }
                child.waitFor(100, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
        } finally {
            cleanup()
            Runtime.getRuntime().removeShutdownHook(shutdownHook)
            System.err.println("MEND locked.")
        }
    }
}
