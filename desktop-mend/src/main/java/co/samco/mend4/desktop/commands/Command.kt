package co.samco.mend4.desktop.commands

import co.samco.mend4.desktop.commands.Command.Companion.HELP_ALIASES
import co.samco.mend4.desktop.output.PrintStreamProvider
import java.util.Collections
import java.util.function.Function
import java.util.stream.Collectors

interface Command {
    val usageText: String
    val descriptionText: String
    val commandAliases: List<String>
    val executionResult: Int

    fun executeCommand(args: List<String>)

    fun isCommandForString(name: String): Boolean

    companion object {
        @JvmField
        val HELP_ALIASES = listOf("-h", "--help")
    }
}

abstract class CommandBase : Command {

    override var executionResult = 0
        protected set

    override fun executeCommand(args: List<String>) {
        if (Collections.disjoint(args, HELP_ALIASES)) execute(args)
        else System.err.println(usageText)
    }

    protected abstract fun execute(args: List<String>)

    override fun isCommandForString(name: String): Boolean {
        return commandAliases.contains(name)
    }

    protected fun executeBehaviourChain(
        behaviourChain: List<Function<List<String>, List<String>?>>,
        args: List<String>
    ) {
        var newArgs = args
        for (f in behaviourChain) {
            newArgs = f.apply(newArgs) ?: break
        }
    }

    protected fun failWithMessage(log: PrintStreamProvider, message: String?) {
        log.err().println(message)
        executionResult = -1
    }

    override fun equals(other: Any?): Boolean {
        return other === this
    }

    override fun hashCode(): Int {
        return commandAliases.joinToString().hashCode()
    }

    override fun toString(): String {
        return commandAliases.stream()
            .collect(Collectors.joining(" | "))
    }
}