package co.samco.mend4.desktop.session

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Starts the interactive shell on behalf of the supervisor; the key JVM is a separate process.
 * Static startup files load the user's configuration and then session.sh. Environment variables
 * tell the shell where to find those files; an anonymous stdin pipe supplies the broker credential
 * and connection details. session.sh reads the pipe, installs the functions, and reconnects stdin
 * to /dev/tty before the shell reads interactive commands. No private key is passed to the shell.
 */
internal object SessionShell {
    private val startupFiles = listOf("session.sh", "bashrc", ".zshrc", ".zshenv")

    fun launch(directory: Path, connection: SessionConnection): Process {
        val shell = System.getenv("SHELL") ?: "/bin/bash"
        val name = Path.of(shell).fileName.toString()
        require(name == "bash" || name == "zsh") { "MEND sessions support Bash and Zsh; set SHELL accordingly." }
        // Copy our packaged scripts, not the user's config. These files contain no credentials.
        startupFiles.forEach { resource ->
            SessionShell::class.java.getResourceAsStream("/session/$resource")!!.use {
                Files.copy(it, directory.resolve(resource))
            }
        }
        // Bash accepts an explicit rcfile. Zsh instead discovers .zshenv/.zshrc via ZDOTDIR below.
        // Both entry scripts load the user's normal config before sourcing our shared session.sh.
        val builder = ProcessBuilder(if (name == "bash") listOf(shell, "--rcfile", directory.resolve("bashrc").toString(), "-i")
            else listOf(shell, "-i"))
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT)
        // These changes affect only the child environment, not the user's original shell.
        builder.environment().apply {
            put("MEND_SESSION_ACTIVE", "1")
            put("MEND_STARTUP_DIR", directory.toString())
            if (name == "zsh") {
                // Remember where the user's config lives, then point Zsh at our startup files.
                // Our .zshenv loads theirs and keeps ZDOTDIR here until our .zshrc is discovered.
                // Our .zshrc restores the original location and loads the user's .zshrc.
                put("MEND_ORIGINAL_ZDOTDIR", get("ZDOTDIR") ?: System.getProperty("user.home"))
                put("ZDOTDIR", directory.toString())
            }
        }
        // Shell functions launch short-lived Java clients; absolute paths keep them working after cd.
        val javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val classpath = System.getProperty("java.class.path").split(File.pathSeparator)
            .joinToString(File.pathSeparator) { Path.of(it).toAbsolutePath().toString() }
        // Must match the four read statements in session.sh. Secrets travel through the pipe,
        // not startup files, environment variables or command-line arguments.
        val values = listOf(connection.credential, connection.socketPath.toString(), javaExecutable, classpath)
        require(values.none { '\n' in it || '\r' in it }) { "Session paths must not contain newlines" }
        val child = builder.start()
        try {
            // Process.outputStream is what the parent writes INTO the child's stdin.
            // Closing this writer ends the bootstrap pipe; session.sh then reads from /dev/tty.
            child.outputStream.bufferedWriter().use { writer -> values.forEach { writer.write(it); writer.newLine() } }
        } catch (e: Exception) { child.destroyForcibly(); throw e }
        return child
    }

    fun removeStartupFiles(directory: Path) {
        // Only remove files we install; attempt every deletion even if one fails.
        startupFiles.forEach { name ->
            runCatching { Files.deleteIfExists(directory.resolve(name)) }
        }
    }
}
