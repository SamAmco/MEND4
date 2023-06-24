package co.samco.mend4.desktop.output

import java.io.PrintStream

interface PrintStreamProvider {
    fun err(): PrintStream
    fun out(): PrintStream
}