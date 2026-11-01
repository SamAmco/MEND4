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

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--session-client") {
        val status = try {
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
        exitProcess(status)
    }
    if (System.getenv("MEND_SESSION_ACTIVE") == "1") {
        System.err.println("mend is unavailable in this session; use dec, merge or lock.")
        exitProcess(2)
    }
    if (args.firstOrNull() in listOf("unlock", "change-password", "setup", "--session-broker") &&
        args.none { it == "--help" || it == "-h" }) {
        try {
            MemoryProtection.prepare(args)
        } catch (e: Throwable) {
            System.err.println("Unable to enable memory protection: ${e.message}")
            exitProcess(1)
        }
    }
    Security.addProvider(BouncyCastleProvider())
    val runner = DaggerCommandRunnerComponent.create()
    if (args.firstOrNull() == "--session-broker") {
        val status = try {
            require(args.size == 3)
            val duration = if (args[2] == "none") null else Duration.ofSeconds(args[2].toLong())
            runner.sessionKeyProcess().run(directory = Path.of(args[1]), duration = duration)
            0
        } catch (e: Exception) {
            System.err.println(e.message ?: "Session broker failed")
            1
        }
        exitProcess(status)
    }
    runner.main.run(runner, args.toList())
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
