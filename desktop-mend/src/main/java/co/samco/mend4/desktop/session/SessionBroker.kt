package co.samco.mend4.desktop.session

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.PrintStream
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Owns the local endpoint. Closing it revokes every connection, including in-flight streams. */
internal class SessionBroker(
    val directory: Path,
    private val operation: (SessionCommandContext) -> Int,
) : AutoCloseable {
    val connection = SessionConnection(
        socketPath = directory.resolve("socket"),
        credential = HexFormat.of().formatHex(ByteArray(32).also(SecureRandom()::nextBytes)),
    )
    private val secret = connection.credential.toByteArray(Charsets.US_ASCII)
    private val listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
    private val clients = ConcurrentHashMap.newKeySet<SocketChannel>()
    private val slots = Semaphore(16)
    private val workers = Executors.newVirtualThreadPerTaskExecutor()
    private val deadlines = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "mend-deadlines").apply { isDaemon = true } }
    val closed = AtomicBoolean(false)

    init {
        try {
            listener.bind(UnixDomainSocketAddress.of(connection.socketPath))
            Files.setPosixFilePermissions(connection.socketPath, PosixFilePermissions.fromString("rw-------"))
        } catch (e: Exception) {
            listener.close()
            workers.shutdownNow()
            deadlines.shutdownNow()
            throw e
        }
    }

    fun start() {
        workers.submit {
            try {
                while (!closed.get()) {
                    val channel = listener.accept()
                    if (!slots.tryAcquire()) { channel.close(); continue }
                    clients.add(channel)
                    workers.submit {
                        try { serve(channel) } finally { clients.remove(channel); slots.release(); channel.close() }
                    }
                }
            } catch (_: AsynchronousCloseException) { /* normal shutdown */ }
        }
    }

    private fun serve(channel: SocketChannel) {
        // A client which stalls before sending a complete authenticated request cannot pin a slot.
        val deadline = deadlines.schedule({ channel.close() }, 5, TimeUnit.SECONDS)
        try {
            val input = DataInputStream(Channels.newInputStream(channel))
            require(input.readInt() == SessionProtocol.VERSION)
            val provided = input.readUTF().toByteArray(Charsets.US_ASCII)
            val authenticated = MessageDigest.isEqual(secret, provided)
            provided.fill(0)
            require(authenticated) { "Invalid session credential" }
            val workingDirectory = input.readUTF()
            val count = input.readInt()
            require(count in 1..16)
            val args = List(count) { input.readUTF() }
            val request = SessionRequest(
                command = args.first(),
                arguments = args.drop(1),
                workingDirectory = workingDirectory,
            )
            deadline.cancel(false)
            val wire = DataOutputStream(Channels.newOutputStream(channel))
            val output = SessionOutput(
                stdout = SessionProtocol.output(wire, SessionProtocol.STDOUT),
                stderr = PrintStream(SessionProtocol.output(wire, SessionProtocol.STDERR), true),
            )
            val context = SessionCommandContext(request = request, output = output)
            val status = try { operation(context) } catch (e: Exception) {
                output.stderr.println(e.message ?: "Session operation failed")
                1
            }
            wire.writeByte(SessionProtocol.EXIT)
            wire.writeInt(status)
            wire.flush()
        } catch (_: Exception) { /* Reject malformed/unauthenticated requests without revealing details. */ }
        finally { deadline.cancel(false) }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        listener.close()
        clients.forEach { runCatching { it.close() } }
        workers.shutdownNow()
        deadlines.shutdownNow()
        secret.fill(0)
        Files.deleteIfExists(connection.socketPath)
    }
}
