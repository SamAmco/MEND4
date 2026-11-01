package co.samco.mend4.desktop.commands

import co.samco.mend4.desktop.output.PrintStreamProvider
import co.samco.mend4.desktop.session.UnlockSession
import javax.inject.Inject

class Unlock @Inject constructor(
    private val session: UnlockSession,
    private val log: PrintStreamProvider,
) : CommandBase() {
    override fun execute(args: List<String>) {
        try { session.run(args) } catch (e: Exception) { failWithMessage(log, e.message) }
    }
    override val usageText = "mend unlock: enter an unlocked shell with dec and lock commands"
    override val descriptionText = "Unlock an in-memory session; exit or lock to discard the key."
    override val commandAliases = listOf(COMMAND_NAME)
    companion object { const val COMMAND_NAME = "unlock" }
}
