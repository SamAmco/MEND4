package co.samco.mend4.desktop.output.impl

import co.samco.mend4.desktop.output.ExitManager
import javax.inject.Inject
import kotlin.system.exitProcess

class ExitManagerImpl @Inject constructor() : ExitManager {
    override fun exit(code: Int) = exitProcess(code)
}