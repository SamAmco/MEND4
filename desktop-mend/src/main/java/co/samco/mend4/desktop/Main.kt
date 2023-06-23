package co.samco.mend4.desktop

import co.samco.mend4.desktop.commands.Command
import co.samco.mend4.desktop.config.CommandsModule
import co.samco.mend4.desktop.config.DesktopModule
import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.output.PrintStreamProvider
import dagger.Component
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.system.exitProcess

class Main @Inject constructor(private val strings: I18N, private val log: PrintStreamProvider) {
    fun run(runner: Runner, args: List<String>) {
        if (args.isEmpty()) {
            runner.defaultCommand().executeCommand(args)
        } else if (printHelp(runner, args)) {
            return
        } else {
            val command = findAndRunCommand(runner.commands(), args)
            if (command == null) {
                log.err().println(strings["Main.commandNotFound"])
                exitProcess(1)
            } else exitProcess(command.executionResult)
        }
    }

    private fun printHelp(runner: Runner, args: List<String>): Boolean {
        if (Command.HELP_ALIASES.contains(args[0])) {
            runner.helpCommand().executeCommand(args.subList(1, args.size))
            return true
        }
        return false
    }

    private fun findAndRunCommand(commands: Set<Command>, args: List<String>): Command? {
        return commands
            .firstOrNull { it.isCommandForString(args[0]) }
            ?.also { it.executeCommand(args.subList(1, args.size)) }
    }

    @Singleton
    @Component(modules = [DesktopModule::class, CommandsModule::class])
    interface Runner {
        fun commands(): Set<Command>

        @Named(CommandsModule.HELP_COMMAND_NAME)
        fun helpCommand(): Command

        @Named(CommandsModule.DEFAULT_COMMAND_NAME)
        fun defaultCommand(): Command
        val main: Main
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val runner = DaggerMain_Runner.create()
            runner.main.run(runner, args.toList())
        }
    }
}