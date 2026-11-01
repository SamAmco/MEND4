package session

import co.samco.mend4.desktop.session.SessionBroker
import co.samco.mend4.desktop.session.SessionProtocol
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class SessionBrokerTest {
    @Test fun streamsBinaryDataAndSeparatesErrorsAndExitCode() {
        val directory = Files.createTempDirectory("mend-test-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        val bytes = ByteArray(25000) { it.toByte() }
        try {
            SessionBroker(directory) { args, cwd, out, err ->
                assertEquals(listOf("dec", "a b"), args)
                assertEquals("/tmp", cwd)
                out.write(bytes)
                err.print("diagnostic")
                7
            }.use { broker ->
                broker.start()
                val out = ByteArrayOutputStream()
                val err = ByteArrayOutputStream()
                assertEquals(7, SessionProtocol.request(broker.socket, broker.credential, listOf("dec", "a b"), "/tmp", out, err))
                assertArrayEquals(bytes, out.toByteArray())
                assertEquals("diagnostic", err.toString())
                assertThrows(Exception::class.java) {
                    SessionProtocol.request(broker.socket, "wrong", listOf("dec"), "/tmp", out, err)
                }
            }
            assertFalse(Files.exists(directory.resolve("socket")))
        } finally { Files.deleteIfExists(directory) }
    }
}
