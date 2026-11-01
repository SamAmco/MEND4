package co.samco.mend4.desktop.session

import co.samco.mend4.desktop.commands.Command

/** One description per public session command, shared by help and argument-error messages. */
internal enum class SessionCommand(val shellName: String, synopsis: String, private val description: String) {
    DEC("dec", "dec [--stdout] [-s] <file or log>", """
        Decrypt a log or encrypted file using this session's key.
        Logs print to stdout. Files normally decrypt into dec-dir and open in an external viewer.
          --stdout  Stream a file to stdout instead of saving and opening it.
          -s        Save a file in dec-dir without opening it.
        Inputs can be configured log names, encrypted file IDs, or paths relative to this shell.
    """.trimIndent()),
    MERGE("merge", "merge <first log> <second log> <output log> | merge <-1|-2> <first log> <second log>", """
        Merge logs in date order, removing duplicate dated entries. Output stays encrypted.
          -1  Replace the first input with the merged log.
          -2  Replace the second input with the merged log.
        Inputs can be configured log names or paths relative to this shell.
        Output paths are relative to this shell; .mend is appended if needed.
    """.trimIndent()),
    LOCK("lock", "lock", """
        Discard this session's key and exit its shell.
        Configured automatic cleanup runs when the shell exits.
        Asking for help does not lock or exit the session.
    """.trimIndent()),
    HELP("help", "help [command]", """
        Show all session commands and their options, or help for one command.
        Global mend commands are unavailable here; run them outside this session.
        In Bash, builtin help still displays Bash's own builtin-command help.
    """.trimIndent());

    val usage = "Usage: $synopsis"
    private val helpText get() = "$usage\n$description\n  -h, --help  Show help without performing the operation.\n"

    companion object {
        /** Intercept help before any decrypt, merge or lock operation can run. */
        fun printHelpIfRequested(context: SessionCommandContext): Boolean {
            val request = context.request
            val command = entries.find { it.shellName == request.command } ?: return false
            val helpRequested = request.arguments.any { it in Command.HELP_ALIASES }
            val text = when {
                command == HELP && (request.arguments.isEmpty() || helpRequested) ->
                    "MEND session commands\n\n" + entries.joinToString("\n") { it.helpText }
                command == HELP -> {
                    require(request.arguments.size == 1) { HELP.usage }
                    val target = entries.find { it.shellName == request.arguments.single() }
                        ?: error("Unknown session command: ${request.arguments.single()}")
                    target.helpText
                }
                helpRequested -> command.helpText
                else -> return false
            }
            context.output.stdout.write(text.toByteArray(Charsets.UTF_8))
            return true
        }
    }
}
