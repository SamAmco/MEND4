package session

import co.samco.mend4.desktop.session.SessionTimeout
import org.junit.Assert.*
import org.junit.Test
import java.time.Duration

class SessionTimeoutTest {
    @Test fun durationParsing() {
        assertEquals(Duration.ofMinutes(15), SessionTimeout.parseDuration("15m"))
        assertEquals(Duration.ofHours(2), SessionTimeout.parseDuration("2h"))
        assertEquals(Duration.ofSeconds(10), SessionTimeout.parseDuration("10s"))
        assertNull(SessionTimeout.parseDuration("none"))
        for (bad in listOf("0s", "-1m", "10", "garbage", "9999999999999999999h")) {
            assertThrows(RuntimeException::class.java) { SessionTimeout.parseDuration(bad) }
        }
    }

    @Test fun warnsOnceRenewsAndExpires() {
        var clock = 0L
        val timeout = SessionTimeout(Duration.ofSeconds(90)) { clock }
        clock = Duration.ofSeconds(60).toNanos()
        assertEquals(SessionTimeout.Event.WARN, timeout.poll())
        assertEquals(SessionTimeout.Event.NONE, timeout.poll())
        timeout.touch()
        clock = Duration.ofSeconds(90).toNanos()
        assertEquals(SessionTimeout.Event.NONE, timeout.poll())
        clock = Duration.ofSeconds(150).toNanos()
        assertEquals(SessionTimeout.Event.EXPIRED, timeout.poll())
    }
}
