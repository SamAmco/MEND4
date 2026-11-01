package session

import co.samco.mend4.desktop.session.SessionBroker
import co.samco.mend4.desktop.session.SessionProtocol
import co.samco.mend4.desktop.session.SessionConnection
import co.samco.mend4.desktop.session.SessionOutput
import co.samco.mend4.desktop.session.SessionRequest
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class SessionBrokerTest {
    @Test fun streamsBinaryDataAndSeparatesErrorsAndExitCode() {
        val directory = Files.createTempDirectory("mend-test-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        val bytes = ByteArray(25000) { it.toByte() }
        try {
            SessionBroker(directory) { context ->
                assertEquals("dec", context.request.command)
                assertEquals(listOf("a b"), context.request.arguments)
                assertEquals("/tmp", context.request.workingDirectory)
                context.output.stdout.write(bytes)
                context.output.stderr.print("diagnostic")
                7
            }.use { broker ->
                broker.start()
                val out = ByteArrayOutputStream()
                val err = ByteArrayOutputStream()
                val request = SessionRequest(command = "dec", arguments = listOf("a b"), workingDirectory = "/tmp")
                val output = SessionOutput(stdout = out, stderr = PrintStream(err, true))
                assertEquals(7, SessionProtocol.request(connection = broker.connection, request = request, output = output))
                assertArrayEquals(bytes, out.toByteArray())
                assertEquals("diagnostic", err.toString())
                assertThrows(Exception::class.java) {
                    SessionProtocol.request(
                        connection = SessionConnection(socketPath = broker.connection.socketPath, credential = "wrong"),
                        request = request,
                        output = output,
                    )
                }
            }
            assertFalse(Files.exists(directory.resolve("socket")))
        } finally { Files.deleteIfExists(directory) }
    }
}
