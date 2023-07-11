package co.samco.mend4.desktop

import co.samco.mend4.desktop.commands.Command
import co.samco.mend4.desktop.config.CommandsModule
import co.samco.mend4.desktop.config.DesktopModule
import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.output.ExitManager
import co.samco.mend4.desktop.output.PrintStreamProvider
import dagger.Component
import java.lang.Exception
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val runner = DaggerCommandRunnerComponent.create()
    runner.main.run(runner, args.toList())
}

@Singleton
@Component(modules = [DesktopModule::class, CommandsModule::class])
interface CommandRunnerComponent {
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