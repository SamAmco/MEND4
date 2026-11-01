package session

import co.samco.mend4.desktop.session.SessionCommand
import co.samco.mend4.desktop.session.SessionCommandContext
import co.samco.mend4.desktop.session.SessionOutput
import co.samco.mend4.desktop.session.SessionRequest
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class SessionCommandTest {
    private fun help(command: String, args: List<String>): Pair<Boolean, String> {
        val stdout = ByteArrayOutputStream()
        val handled = SessionCommand.printHelpIfRequested(SessionCommandContext(
            request = SessionRequest(command, args, "/tmp"),
            output = SessionOutput(stdout, PrintStream(ByteArrayOutputStream())),
        ))
        return handled to stdout.toString("UTF-8")
    }

    @Test fun overviewListsPublicCommandsAndOptions() {
        val (handled, text) = help("help", emptyList())
        assertTrue(handled)
        SessionCommand.entries.forEach { assertTrue(text.contains(it.usage)) }
        assertTrue(text.contains("--stdout"))
        assertTrue(text.contains("-1"))
        assertFalse(text.contains("Usage: touch"))
        assertEquals(text, help("help", listOf("--help")).second)
    }

    @Test fun commandHelpAndBothFlagsUseTheSameDescription() {
        for (command in SessionCommand.entries.filter { it != SessionCommand.HELP }) {
            val specific = help("help", listOf(command.shellName))
            assertTrue(specific.first)
            assertTrue(specific.second.startsWith(command.usage))
            for (flag in listOf("-h", "--help")) {
                assertEquals(specific, help(command.shellName, listOf(flag)))
                // Help takes precedence over operation arguments and their validation.
                assertEquals(specific, help(command.shellName, listOf("missing-file", flag)))
            }
        }
    }

    @Test fun ordinaryOperationsAreNotHandledAsHelp() {
        for (command in listOf("dec", "merge", "lock", "touch", "unknown")) {
            assertEquals(false to "", help(command, emptyList()))
        }
        assertThrows(RuntimeException::class.java) { help("help", listOf("unknown")) }
        assertThrows(RuntimeException::class.java) { help("help", listOf("dec", "merge")) }
    }
}
