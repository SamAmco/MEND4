package co.samco.mend4.desktop.commands

import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.helper.VersionHelper
import co.samco.mend4.desktop.output.PrintStreamProvider
import java.util.stream.Collectors
import javax.inject.Inject

class Version @Inject constructor(
    private val strings: I18N,
    private val log: PrintStreamProvider,
    private val versionHelper: VersionHelper
) : Command() {
    override val commandAliases = listOf(COMMAND_NAME, "--version", "-version", "-V")

    public override fun execute(args: List<String>) {
        log.err().println(strings.getf("Version.desktopVersion", versionHelper.version))
    }

    override val usageText: String
        get() = strings.getf(
            "Version.usage",
            commandAliases.stream().collect(Collectors.joining(" | "))
        )
    override val descriptionText: String
        get() = strings["Version.description"]

    companion object {
        const val COMMAND_NAME = "-v"
    }
}