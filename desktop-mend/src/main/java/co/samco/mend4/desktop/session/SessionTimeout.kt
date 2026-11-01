package co.samco.mend4.desktop.session

import java.time.Duration

/** Monotonic inactivity clock. Only an authenticated request or a shell prompt renews it. */
internal class SessionTimeout(private val duration: Duration?, private val now: () -> Long = System::nanoTime) {
    private var lastActivity = now()
    private var warned = false

    @Synchronized fun touch() { lastActivity = now(); warned = false }

    @Synchronized fun poll(): Event {
        val limit = duration?.toNanos() ?: return Event.NONE
        val remaining = limit - (now() - lastActivity)
        if (remaining <= 0) return Event.EXPIRED
        if (!warned && remaining <= minOf(Duration.ofSeconds(30).toNanos(), limit / 3)) {
            warned = true
            return Event.WARN
        }
        return Event.NONE
    }

    enum class Event { NONE, WARN, EXPIRED }

    companion object {
        fun parseDuration(value: String): Duration? {
            if (value == "none") return null
            val match = Regex("([1-9][0-9]*)(s|m|h)").matchEntire(value)
                ?: error("Timeout must be a positive duration such as 30s, 15m, 2h, or none")
            val amount = match.groupValues[1].toLongOrNull() ?: error("Timeout is too large")
            val seconds = try { Math.multiplyExact(amount, when (match.groupValues[2]) { "h" -> 3600L; "m" -> 60L; else -> 1L }) }
                catch (_: ArithmeticException) { error("Timeout is too large") }
            require(seconds <= 365L * 24 * 3600) { "Timeout must be at most one year; use --no-timeout to disable it" }
            return Duration.ofSeconds(seconds)
        }
    }
}
