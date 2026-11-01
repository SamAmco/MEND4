package co.samco.mend4.desktop.session

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.PrintStream
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.security.MessageDigest

/** Small bounded protocol. No Java object deserialization, shell evaluation or key export. */
internal object SessionProtocol {
    const val VERSION = 1
    const val STDOUT = 1
    const val STDERR = 2
    const val EXIT = 3
    const val MAX_CHUNK = 8192
    private const val MAX_ARGUMENTS = 16

    // Request layout: version, credential, working directory, argument count, arguments.
    // The first argument is the command. Authenticate before reading the command payload.
    fun readAuthenticatedRequest(input: DataInputStream, expectedCredential: ByteArray): SessionRequest {
        require(input.readInt() == VERSION)
        val provided = input.readUTF().toByteArray(Charsets.US_ASCII)
        val authenticated = MessageDigest.isEqual(expectedCredential, provided)
        provided.fill(0)
        require(authenticated) { "Invalid session credential" }
        val workingDirectory = input.readUTF()
        val count = input.readInt()
        require(count in 1..MAX_ARGUMENTS)
        val args = List(count) { input.readUTF() }
        return SessionRequest(
            command = args.first(),
            arguments = args.drop(1),
            workingDirectory = workingDirectory,
        )
    }

    private fun writeRequest(output: DataOutputStream, credential: String, request: SessionRequest) {
        val args = listOf(request.command) + request.arguments
        require(args.size in 1..MAX_ARGUMENTS) { "Too many session arguments" }
        output.writeInt(VERSION)
        output.writeUTF(credential)
        output.writeUTF(request.workingDirectory)
        output.writeInt(args.size)
        args.forEach(output::writeUTF)
        output.flush()
    }

    fun output(wire: DataOutputStream, kind: Int): OutputStream = object : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()))
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            var position = offset
            while (position < offset + length) {
                val count = minOf(MAX_CHUNK, offset + length - position)
                wire.writeByte(kind)
                wire.writeInt(count)
                wire.write(bytes, position, count)
                wire.flush()
                position += count
            }
        }
    }

    fun request(connection: SessionConnection, request: SessionRequest, output: SessionOutput): Int {
        SocketChannel.open(UnixDomainSocketAddress.of(connection.socketPath)).use { channel ->
            val writer = DataOutputStream(Channels.newOutputStream(channel))
            writeRequest(output = writer, credential = connection.credential, request = request)
            val reader = DataInputStream(Channels.newInputStream(channel))
            while (true) {
                when (val kind = reader.readUnsignedByte()) {
                    STDOUT, STDERR -> {
                        val size = reader.readInt()
                        require(size in 0..MAX_CHUNK) { "Invalid session response" }
                        val bytes = ByteArray(size)
                        reader.readFully(bytes)
                        val destination = if (kind == STDOUT) output.stdout else output.stderr
                        destination.write(bytes)
                        destination.flush()
                        if (output.stdout is PrintStream && output.stdout.checkError()) throw IOException("Output pipe closed")
                    }
                    EXIT -> return reader.readInt()
                    else -> error("Invalid session response")
                }
            }
        }
    }
}
