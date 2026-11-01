package co.samco.mend4.desktop.session

import co.samco.mend4.desktop.dao.OSDao
import co.samco.mend4.desktop.dao.SettingsDao
import co.samco.mend4.desktop.commands.Clean
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class UnlockSession @Inject constructor(
    private val os: OSDao,
    private val settings: SettingsDao,
    private val clean: Clean,
) {
    fun run(args: List<String>) {
        val options = SessionOptions.parse(
            args = args,
            configuredTimeout = settings.getValue(SettingsDao.SESSION_TIMEOUT),
            configuredAutoClean = settings.getValue(SettingsDao.SESSION_AUTO_CLEAN),
        )
        require(System.getProperty("os.name").let { it == "Linux" || it == "Mac OS X" }) { "Sessions require Linux or macOS" }
        require(System.console() != null) { "mend unlock requires an interactive terminal" }
        val shellName = Path.of(System.getenv("SHELL") ?: "/bin/bash").fileName.toString()
        require(shellName in listOf("bash", "zsh")) { "MEND sessions support Bash and Zsh; set SHELL accordingly." }
        val directory = Files.createTempDirectory(Path.of("/tmp"), "mend-",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        var child: Process? = null
        val broker = try {
            val password = os.readPassword("Password: ")
            try {
                SessionKeyProcess.start(directory = directory, duration = options.timeout, password = password)
            } finally {
                password.fill('\u0000')
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
                if (!it.waitFor(500, TimeUnit.MILLISECONDS)) it.destroyForcibly()
            }
            SessionShell.removeStartupFiles(directory)
            runCatching { Files.deleteIfExists(directory) }
            // Wait until the shell has ended, even if the key expired earlier in an editor.
            // Use the same implementation as mend clean; it reports failures to stderr.
            if (options.autoClean && child != null) clean.executeCommand(emptyList())
        }
        val shutdownHook = Thread(::cleanup, "mend-session-cleanup")
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        try {
            child = SessionShell.launch(directory = directory, connection = broker.connection)
            // Keep the terminal's parent command alive even after the key process expires.
            // A foreground editor can finish normally; the shell exits at its next failed touch.
            child.waitFor()
        } finally {
            cleanup()
            Runtime.getRuntime().removeShutdownHook(shutdownHook)
        }
    }
}
