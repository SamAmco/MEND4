package co.samco.mend4.desktop.session

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** stdin is the anonymous bootstrap pipe; startup reconnects it to /dev/tty before reading commands. */
internal object SessionShell {
    fun launch(directory: Path, connection: SessionConnection): Process {
        val shell = System.getenv("SHELL") ?: "/bin/bash"
        val name = Path.of(shell).fileName.toString()
        require(name == "bash" || name == "zsh") { "MEND sessions support Bash and Zsh; set SHELL accordingly." }
        listOf("session.sh", "bashrc", ".zshrc", ".zshenv").forEach { resource ->
            SessionShell::class.java.getResourceAsStream("/session/$resource")!!.use {
                Files.copy(it, directory.resolve(resource))
            }
        }
        val builder = ProcessBuilder(if (name == "bash") listOf(shell, "--rcfile", directory.resolve("bashrc").toString(), "-i")
            else listOf(shell, "-i"))
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT)
        builder.environment().apply {
            put("MEND_SESSION_ACTIVE", "1")
            put("MEND_STARTUP_DIR", directory.toString())
            if (name == "zsh") {
                put("MEND_ORIGINAL_ZDOTDIR", get("ZDOTDIR") ?: System.getProperty("user.home"))
                put("ZDOTDIR", directory.toString())
            }
        }
        val javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val classpath = System.getProperty("java.class.path").split(File.pathSeparator)
            .joinToString(File.pathSeparator) { Path.of(it).toAbsolutePath().toString() }
        val values = listOf(connection.credential, connection.socketPath.toString(), javaExecutable, classpath)
        require(values.none { '\n' in it || '\r' in it }) { "Session paths must not contain newlines" }
        val child = builder.start()
        try {
            child.outputStream.bufferedWriter().use { writer -> values.forEach { writer.write(it); writer.newLine() } }
        } catch (e: Exception) { child.destroyForcibly(); throw e }
        return child
    }
}
