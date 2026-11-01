package co.samco.mend4.desktop.commands

import co.samco.mend4.desktop.output.PrintStreamProvider
import co.samco.mend4.desktop.session.UnlockSession
import co.samco.mend4.desktop.session.SessionOptions
import javax.inject.Inject

/**
 * Entry point for `mend unlock`: opens a shell whose decryption service has a separate lifetime.
 *
 * The JVM executing this command is the supervisor, running [UnlockSession]. Before this command
 * runs, Main relaunches MEND with memory-protection flags if needed; that initial JVM only waits
 * for the protected supervisor. The supervisor then creates two separate child processes:
 *
 * ```text
 * Supervisor JVM (UnlockSession)
 * ├── Key JVM (SessionKeyProcess): private key, Unix-socket broker, inactivity timer
 * └── Interactive Bash/Zsh: dec/merge/lock functions, pipelines, editors and other commands
 * ```
 *
 * Startup proceeds as follows:
 * 1. The supervisor reads the password and sends its characters to the key JVM through an
 *    anonymous pipe. Both JVMs clear their password arrays; only the key JVM decrypts the key.
 * 2. The key JVM starts its broker and returns a random session credential and socket path
 *    through another anonymous pipe. The credential authorizes requests; it is not the key.
 * 3. The supervisor launches the shell through SessionShell, passing the credential, socket path
 *    and Java launch details through the shell's stdin pipe. Static startup scripts loaded from
 *    the session directory define dec/merge/lock and the prompt hook; the functions are not piped in.
 *    The scripts store the credential in a non-exported variable and reconnect stdin to /dev/tty.
 * 4. Each dec/merge/lock call starts a short-lived Java client which authenticates to the broker over
 *    the Unix socket. Returned stdout/stderr feed the shell's normal pipes and redirections.
 *    The prompt hook sends a touch request to renew the inactivity timer.
 *
 * The private key is never sent to the supervisor, shell or clients, or written to a file.
 * The supervisor keeps the key JVM's input pipe open; closing it on shell exit (or losing it
 * when the supervisor dies) asks the key JVM to lock and exit. The lock function also exits
 * the shell. On timeout, only the key JVM exits: the supervisor continues waiting for the shell,
 * so a foreground editor can keep running with plaintext it already received. When the shell
 * next prepares a prompt, its failed touch request makes it exit. The supervisor then cleans
 * up the session's startup files and temporary directory, then runs the same cleaning logic as
 * mend clean on the configured dec-dir. This is enabled by default; session-auto-clean configures
 * the default, and --auto-clean / --no-auto-clean override it for this session. Cleaning waits
 * for shell exit rather than key expiry, so a foreground editor can finish before it happens.
 */
class Unlock @Inject constructor(
    private val session: UnlockSession,
    private val log: PrintStreamProvider,
) : CommandBase() {
    override fun execute(args: List<String>) {
        try { session.run(args) } catch (e: Exception) { failWithMessage(log, e.message) }
    }
    override val usageText = "${SessionOptions.USAGE}: enter a shell with dec, merge and lock"
    override val descriptionText = "Unlock an in-memory session; exit or lock to discard the key."
    override val commandAliases = listOf(COMMAND_NAME)
    companion object { const val COMMAND_NAME = "unlock" }
}
