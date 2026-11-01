package commands

import co.samco.mend4.desktop.commands.Unlock
import co.samco.mend4.desktop.session.UnlockSession
import co.samco.mend4.desktop.session.SessionDecrypt
import com.nhaarman.mockitokotlin2.*
import org.junit.Test

class UnlockTest : TestBase() {
    @Test fun refusesWithoutAnInteractiveTerminalAndNeverWritesKeys() {
        val session = UnlockSession(cryptoProvider, SessionDecrypt(cryptoProvider, fileResolveHelper, settings, osDao), osDao, fileResolveHelper)
        Unlock(session, log).executeCommand(emptyList())
        verify(err).println("mend unlock requires an interactive terminal")
        verify(osDao, never()).fileOutputSteam(any(), any())
    }
}
