package session

import co.samco.mend4.desktop.session.SessionOptions
import org.junit.Assert.*
import org.junit.Test
import java.time.Duration

class SessionOptionsTest {
    @Test fun defaultsAndConfiguration() {
        val defaults = SessionOptions.parse(emptyList(), null, null)
        assertEquals(Duration.ofMinutes(15), defaults.timeout)
        assertTrue(defaults.autoClean)
        val configured = SessionOptions.parse(emptyList(), "2h", "false")
        assertEquals(Duration.ofHours(2), configured.timeout)
        assertFalse(configured.autoClean)
    }

    @Test fun flagsOverrideSettingsAndCanBeCombinedInEitherOrder() {
        val enabled = SessionOptions.parse(listOf("--auto-clean", "--timeout", "10s"), "2h", "false")
        assertTrue(enabled.autoClean)
        assertEquals(Duration.ofSeconds(10), enabled.timeout)
        val disabled = SessionOptions.parse(listOf("--no-timeout", "--no-auto-clean"), "2h", "true")
        assertFalse(disabled.autoClean)
        assertNull(disabled.timeout)
        assertTrue(SessionOptions.parse(listOf("--auto-clean"), null, null).autoClean)
        assertFalse(SessionOptions.parse(listOf("--no-auto-clean"), null, null).autoClean)
    }

    @Test fun rejectsUnknownMissingAndConflictingOptions() {
        for (args in listOf(
            listOf("--unknown"), listOf("--timeout"), listOf("--timeout", "invalid"),
            listOf("--timeout", "1m", "--no-timeout"), listOf("--no-timeout", "--timeout", "1m"),
            listOf("--auto-clean", "--no-auto-clean"), listOf("--no-auto-clean", "--auto-clean"),
            listOf("--auto-clean", "--auto-clean"), listOf("--no-timeout", "--no-timeout"),
        )) {
            assertThrows(RuntimeException::class.java) { SessionOptions.parse(args, null, null) }
        }
        assertThrows(RuntimeException::class.java) { SessionOptions.parse(emptyList(), null, "maybe") }
    }
}
