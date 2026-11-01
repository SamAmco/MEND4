package co.samco.mend4.desktop

import co.samco.mend4.desktop.commands.Command
import co.samco.mend4.desktop.config.CommandsModule
import co.samco.mend4.desktop.config.DesktopModule
import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.output.ExitManager
import co.samco.mend4.desktop.output.PrintStreamProvider
import co.samco.mend4.desktop.session.MemoryProtection
import co.samco.mend4.desktop.session.SessionConnection
import co.samco.mend4.desktop.session.SessionOutput
import co.samco.mend4.desktop.session.SessionProtocol
import co.samco.mend4.desktop.session.SessionRequest
import co.samco.mend4.desktop.session.SessionKeyProcess
import dagger.Component
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import java.nio.file.Path
import java.time.Duration
import kotlin.system.exitProcess
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Routes this JVM to a normal command, a short-lived session client, or the key-holding broker. */
fun main(args: Array<String>) {
    // Shell-local commands must reach their broker even though global mend calls are blocked.
    if (args.firstOrNull() == "--session-client") {
        exitProcess(runSessionClient(args))
    }

    refuseGlobalCommandsInsideSession()
    prepareMemoryProtection(args)
    val runner = createCommandRunner()

    if (args.firstOrNull() == "--session-broker") {
        exitProcess(runSessionBroker(runner, args))
    }
    runner.main.run(runner, args.toList())
}

/**
 * Internal mode: `--session-client <socket> <command>`, followed by any command arguments.
 * The session shell starts one client for each dec/merge/lock call and prompt touch.
 * It reads the credential from a stdin pipe, forwards the request to the broker, streams
 * the response to stdout/stderr, then exits with the returned status. It never holds the key
 * and does not need the cryptography provider or command dependencies initialized.
 */
private fun runSessionClient(args: Array<String>): Int = try {
    require(args.size >= 3)
    val credential = System.`in`.bufferedReader().readLine() ?: error("Missing session credential")
    SessionProtocol.request(
        connection = SessionConnection(socketPath = Path.of(args[1]), credential = credential),
        request = SessionRequest(
            command = args[2],
            arguments = args.drop(3),
            workingDirectory = System.getProperty("user.dir"),
        ),
        output = SessionOutput(stdout = System.out, stderr = System.err),
    )
} catch (e: Exception) {
    System.err.println("MEND session unavailable: ${e.message ?: "connection closed"}")
    1
}

private fun refuseGlobalCommandsInsideSession() {
    if (System.getenv("MEND_SESSION_ACTIVE") == "1") {
        System.err.println("mend is unavailable in this session; use dec, merge or lock.")
        exitProcess(2)
    }
}

/** Apply protection before any password or private key is loaded; this may relaunch the JVM. */
private fun prepareMemoryProtection(args: Array<String>) {
    val handlesSecrets = args.firstOrNull() in listOf("unlock", "change-password", "setup", "--session-broker")
    val helpRequested = args.any { it in Command.HELP_ALIASES }
    if (!handlesSecrets || helpRequested) return

    try {
        MemoryProtection.prepare(args)
    } catch (e: Throwable) {
        System.err.println("Unable to enable memory protection: ${e.message}")
        exitProcess(1)
    }
}

private fun createCommandRunner(): CommandRunnerComponent {
    Security.addProvider(BouncyCastleProvider())
    return DaggerCommandRunnerComponent.create()
}

/**
 * Internal mode: `--session-broker <session-directory> <timeout-seconds|none>`.
 * UnlockSession starts this protected child JVM to own the private key and serve Unix-socket
 * requests. SessionKeyProcess reads the password from stdin and returns connection details on
 * stdout, both through private pipes. It runs until lock, timeout or supervisor disconnection;
 * main then exits this JVM to release the key. The supervisor owns and waits for the shell.
 */
private fun runSessionBroker(runner: CommandRunnerComponent, args: Array<String>): Int = try {
    require(args.size == 3)
    val duration = if (args[2] == "none") null else Duration.ofSeconds(args[2].toLong())
    runner.sessionKeyProcess().run(directory = Path.of(args[1]), duration = duration)
    0
} catch (e: Exception) {
    System.err.println(e.message ?: "Session broker failed")
    1
}

@Singleton
@Component(modules = [DesktopModule::class, CommandsModule::class])
interface CommandRunnerComponent {
    fun sessionKeyProcess(): SessionKeyProcess

    fun commands(): Set<Command>

    @Named(CommandsModule.HELP_COMMAND_NAME)
    fun helpCommand(): Command

    @Named(CommandsModule.DEFAULT_COMMAND_NAME)
    fun defaultCommand(): Command

    val main: Main
}

class Main @Inject constructor(
    private val strings: I18N,
    private val log: PrintStreamProvider,
    private val exitManager: ExitManager
) {

    fun run(commandRunnerComponent: CommandRunnerComponent, args: List<String>) {
        if (args.isEmpty()) {
            commandRunnerComponent.defaultCommand().executeCommand(args)
        } else if (printHelp(commandRunnerComponent, args)) {
            return
        } else {
            val command = findAndRunCommand(commandRunnerComponent.commands(), args)
            if (command == null) {
                log.err().println(strings["Main.commandNotFound"])
                exitManager.exit(1)
            } else exitManager.exit(command.executionResult)
        }
    }

    private fun printHelp(
        commandRunnerComponent: CommandRunnerComponent,
        args: List<String>
    ): Boolean {
        if (Command.HELP_ALIASES.contains(args[0])) {
            commandRunnerComponent.helpCommand().executeCommand(args.subList(1, args.size))
            return true
        }
        return false
    }

    private fun findAndRunCommand(commands: Set<Command>, args: List<String>): Command? {
        return commands
            .firstOrNull { it.isCommandForString(args[0]) }
            ?.also { it.executeCommand(args.subList(1, args.size)) }
    }
}
