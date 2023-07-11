package co.samco.mend4.desktop.commands

import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.output.PrintStreamProvider
import java.util.stream.Collectors
import javax.inject.Inject

class Help @Inject constructor(
    private val strings: I18N,
    private val log: PrintStreamProvider,
    private val commands: Set<@JvmSuppressWildcards Command>
) : CommandBase() {

    companion object {
        const val HELP_FLAG = "-h"
    }

    override var commandAliases = listOf(HELP_FLAG, "--help")

    override fun execute(args: List<String>) {
        log.err().println(usageText)
    }

    override val usageText: String
        get() {
            val sb = StringBuilder()
            appendMendUsage(sb)
            appendCommandDescriptions(sb)
            return sb.toString()
        }

    private fun appendCommandDescriptions(sb: StringBuilder) {
        sb.append(strings["Help.commands"])
        sb.append(strings.newLine)
        val it = commands.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            sb.append("\t")
            appendCommandDescription(sb, entry)
            sb.append(strings.newLine)
        }
    }

    private fun appendCommandDescription(sb: StringBuilder, command: Command) {
        sb.append(command.commandAliases.stream().collect(Collectors.joining(", ")))
        sb.append("\t:\t")
        sb.append(command.descriptionText)
    }

    private fun appendMendUsage(sb: StringBuilder) {
        sb.append(strings.getf("Help.usage", Version.COMMAND_NAME, HELP_FLAG, HELP_FLAG))
        sb.append(strings.newLine)
        sb.append(strings.newLine)
    }

    override val descriptionText: String
        get() = strings["Help.description"]
}