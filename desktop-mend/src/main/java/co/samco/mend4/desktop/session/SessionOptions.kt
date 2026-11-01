package co.samco.mend4.desktop.session

import java.time.Duration

internal data class SessionOptions(val timeout: Duration?, val autoClean: Boolean) {
    companion object {
        const val USAGE = "mend unlock [--timeout <30s|15m|2h> | --no-timeout] [--auto-clean | --no-auto-clean]"

        fun parse(args: List<String>, configuredTimeout: String?, configuredAutoClean: String?): SessionOptions {
            var timeout: String? = null
            var autoClean: Boolean? = null
            val arguments = args.iterator()
            while (arguments.hasNext()) {
                when (val argument = arguments.next()) {
                    "--timeout" -> {
                        require(timeout == null && arguments.hasNext()) { "Usage: $USAGE" }
                        timeout = arguments.next()
                    }
                    "--no-timeout" -> {
                        require(timeout == null) { "Usage: $USAGE" }
                        timeout = "none"
                    }
                    "--auto-clean", "--no-auto-clean" -> {
                        require(autoClean == null) { "Usage: $USAGE" }
                        autoClean = argument == "--auto-clean"
                    }
                    else -> error("Usage: $USAGE")
                }
            }
            return SessionOptions(
                timeout = SessionTimeout.parseDuration(timeout ?: configuredTimeout ?: "15m"),
                autoClean = autoClean ?: when (configuredAutoClean) {
                    null, "true" -> true
                    "false" -> false
                    else -> error("session-auto-clean must be true or false")
                },
            )
        }
    }
}
