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
            when (context.request.command) {
                "dec" -> decrypt.decrypt(key = result.privateKey, context = context)
                "lock" -> locked.set(true)
                "touch" -> Unit
                else -> error("Unknown session command")
            }
            0
        }
        val shutdownHook = Thread({ runCatching { broker.close() } }, "mend-key-cleanup")
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        try {
            broker.start()
            // stdout is a private bootstrap pipe to the supervisor, never terminal output.
            DataOutputStream(System.out).apply {
                writeUTF(broker.connection.socketPath.toString())
                writeUTF(broker.connection.credential)
                flush()
            }
            // The supervisor keeps stdin open while the shell lives. EOF also handles
            // supervisor failure, so an abandoned key process does not remain unlocked.
            Thread.ofVirtual().start {
                try { input.read() } finally { locked.set(true) }
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
