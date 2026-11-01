package co.samco.mend4.desktop.session

import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.core.crypto.UnlockResult
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/** Owns the private key in a separate JVM, which can exit while the session shell keeps running. */
class SessionKeyProcess @Inject constructor(
    private val crypto: CryptoProvider,
    private val decrypt: SessionDecrypt,
    private val merge: SessionMerge,
) {
    internal fun run(directory: Path, duration: Duration?) {
        val input = DataInputStream(System.`in`)
        val length = input.readInt()
        require(length in 0..MAX_PASSWORD_LENGTH) { "Invalid password length" }
        val password = CharArray(length)
        val result = try {
            password.indices.forEach { password[it] = input.readChar() }
            crypto.unlock(password)
        } finally {
            password.fill('\u0000')
        }
        require(result is UnlockResult.Success) { "Incorrect password" }

        val timeout = SessionTimeout(duration)
        val locked = AtomicBoolean(false)
        val broker = SessionBroker(directory) { context ->
            check(!locked.get()) { "MEND is locked" }
            timeout.touch()
            if (SessionCommand.printHelpIfRequested(context)) return@SessionBroker 0
            when (context.request.command) {
                "dec" -> decrypt.decrypt(key = result.privateKey, context = context)
                "merge" -> merge.merge(key = result.privateKey, context = context)
                "lock" -> {
                    require(context.request.arguments.isEmpty()) { SessionCommand.LOCK.usage }
                    locked.set(true)
                }
                "touch" -> Unit
                else -> error("Unknown session command")
            }
            0
        }
        val shutdownHook = Thread({ runCatching { broker.close() } }, "mend-key-cleanup")
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        try {
            broker.start()
            // The supervisor launched this JVM with stdout connected to a pipe it reads.
            // Send it the socket address and credential for the broker we just started.
            // UnlockSession (the supervisor) passes them to SessionShell,
            // which delivers them to the new shell through a separate stdin pipe. The shell's
            // dec/merge/lock clients use them to connect and authenticate; the key stays here.
            // writeUTF/readUTF encode string lengths, so these are not newline-separated lines.
            // Flush now so the waiting supervisor can read the details and launch the shell.
            DataOutputStream(System.out).apply {
                writeUTF(broker.connection.socketPath.toString())
                writeUTF(broker.connection.credential)
                flush()
            }
            // This input is a pipe from the supervisor, not the user's keyboard. The password
            // has already been read; no more bytes are expected, but the supervisor leaves
            // the pipe open while the session shell runs. read() therefore blocks here.
            // When the shell exits, the supervisor closes the pipe and read() returns -1 (EOF).
            // If the supervisor dies, the OS closes its end of the pipe with the same result.
            // Wait in a separate thread so the main loop can keep checking the timeout.
            Thread.ofVirtual().start {
                try {
                    input.read()
                } finally {
                    // EOF, a read failure, or an unexpected byte all request shutdown.
                    // The main loop below sees this flag and closes the broker before exiting.
                    locked.set(true)
                }
            }
            timeout.touch()
            while (!locked.get()) {
                when (timeout.poll()) {
                    SessionTimeout.Event.WARN -> System.err.println("\nMEND will lock shortly. Press Enter at the shell prompt to remain unlocked.")
                    SessionTimeout.Event.EXPIRED -> {
                        System.err.println("\nMEND session timed out. Open programs may continue; decryption is disabled.")
                        locked.set(true)
                    }
                    SessionTimeout.Event.NONE -> Unit
                }
                Thread.sleep(100)
            }
        } finally {
            broker.close()
            Runtime.getRuntime().removeShutdownHook(shutdownHook)
            System.err.println("MEND locked.")
        }
        // Main exits this JVM after run() returns, releasing the key and any provider copies.
    }

    companion object {
        private const val MAX_PASSWORD_LENGTH = 65536

        internal fun start(directory: Path, duration: Duration?, password: CharArray): RunningSessionKeyProcess {
            require(password.size <= MAX_PASSWORD_LENGTH) { "Password is too long" }
            val child = ProcessBuilder(MemoryProtection.command(listOf(
                "--session-broker", directory.toString(), duration?.seconds?.toString() ?: "none",
            ))).redirectError(ProcessBuilder.Redirect.INHERIT).start()
            try {
                // No immutable password String: send the characters directly through stdin.
                DataOutputStream(child.outputStream).apply {
                    writeInt(password.size)
                    password.forEach { writeChar(it.code) }
                    flush()
                }
                val bootstrap = DataInputStream(child.inputStream)
                val connection = SessionConnection(
                    socketPath = Path.of(bootstrap.readUTF()),
                    credential = bootstrap.readUTF(),
                )
                return RunningSessionKeyProcess(child, connection)
            } catch (e: Exception) {
                child.destroy()
                child.waitFor()
                error("Unable to unlock session; see broker error above")
            }
        }
    }
}

/** The supervisor owns only a credential and process handle, never the decrypted key. */
internal class RunningSessionKeyProcess(
    private val process: Process,
    val connection: SessionConnection,
) : AutoCloseable {
    override fun close() {
        // EOF asks the broker to lock. It may already have exited on timeout or explicit lock.
        runCatching { process.outputStream.close() }
        if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            process.waitFor()
        }
        process.inputStream.close()
        Files.deleteIfExists(connection.socketPath)
    }
}
