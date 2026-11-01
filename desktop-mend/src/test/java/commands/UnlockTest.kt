package commands

import co.samco.mend4.desktop.commands.Unlock
import co.samco.mend4.desktop.commands.Clean
import co.samco.mend4.desktop.session.UnlockSession
import com.nhaarman.mockitokotlin2.*
import org.junit.Test

class UnlockTest : TestBase() {
    @Test fun refusesWithoutAnInteractiveTerminalAndNeverWritesKeys() {
        val clean = Clean(strings, log, settingsHelper, shredHelper, settings)
        val session = UnlockSession(os = osDao, settings = settings, clean = clean)
        Unlock(session, log).executeCommand(emptyList())
        verify(err).println("mend unlock requires an interactive terminal")
        verify(osDao, never()).fileOutputSteam(any(), any())
    }
}
