package co.samco.mend4.desktop.output.impl

import co.samco.mend4.desktop.output.PrintStreamProvider
import java.io.PrintStream

class SystemPrintStreamProviderImpl : PrintStreamProvider {
    override fun err(): PrintStream = System.err

    override fun out(): PrintStream = System.out
}