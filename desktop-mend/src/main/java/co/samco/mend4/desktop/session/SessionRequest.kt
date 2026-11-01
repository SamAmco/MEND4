package co.samco.mend4.desktop.session

import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Path

internal data class SessionRequest(
    val command: String,
    val arguments: List<String>,
    val workingDirectory: String,
)

internal data class SessionOutput(
    val stdout: OutputStream,
    val stderr: PrintStream,
)

/** An authenticated request and the streams belonging to that client's response. */
internal data class SessionCommandContext(
    val request: SessionRequest,
    val output: SessionOutput,
)

// Deliberately not a data class: generated toString() must not include the credential.
internal class SessionConnection(
    val socketPath: Path,
    val credential: String,
)
