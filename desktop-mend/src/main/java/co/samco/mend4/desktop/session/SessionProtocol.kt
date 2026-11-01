package co.samco.mend4.desktop.session

import java.io.*
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.file.Path

/** Small bounded protocol. No Java object deserialization, shell evaluation or key export. */
internal object SessionProtocol {
    const val VERSION = 1
    const val STDOUT = 1
    const val STDERR = 2
    const val EXIT = 3
    const val MAX_CHUNK = 8192

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

    fun request(socket: Path, credential: String, args: List<String>, cwd: String,
                stdout: OutputStream, stderr: OutputStream): Int {
        require(args.size in 1..16) { "Too many session arguments" }
        SocketChannel.open(UnixDomainSocketAddress.of(socket)).use { channel ->
            val writer = DataOutputStream(Channels.newOutputStream(channel))
            writer.writeInt(VERSION)
            writer.writeUTF(credential)
            writer.writeUTF(cwd)
            writer.writeInt(args.size)
            args.forEach(writer::writeUTF)
            writer.flush()
            val reader = DataInputStream(Channels.newInputStream(channel))
            while (true) {
                when (val kind = reader.readUnsignedByte()) {
                    STDOUT, STDERR -> {
                        val size = reader.readInt()
                        require(size in 0..MAX_CHUNK) { "Invalid session response" }
                        val bytes = ByteArray(size)
                        reader.readFully(bytes)
                        (if (kind == STDOUT) stdout else stderr).apply { write(bytes); flush() }
                        if (stdout is PrintStream && stdout.checkError()) throw IOException("Output pipe closed")
                    }
                    EXIT -> return reader.readInt()
                    else -> error("Invalid session response")
                }
            }
        }
    }
}
