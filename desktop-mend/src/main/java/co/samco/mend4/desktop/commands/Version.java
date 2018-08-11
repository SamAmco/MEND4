package co.samco.mend4.desktop.commands;

import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.output.PrintStreamProvider;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Version extends Command {
    public static final String COMMAND_NAME = "-v";
    private final List<String> COMMAND_ALIASES = Arrays.asList(COMMAND_NAME, "--version");

    private final I18N strings;
    private final PrintStreamProvider log;

    @Inject
    public Version(I18N strings, PrintStreamProvider log) {
        this.strings = strings;
        this.log = log;
    }

    @Override
    public void execute(List<String> args) {
        //TODO make sure the version number is actually correct
        log.err().println(strings.getf("Version.desktopVersion",
                getClass().getPackage().getImplementationVersion()));
    }

    @Override
    public String getUsageText() {
        return strings.getf("Version.usage",
                COMMAND_ALIASES.stream().collect(Collectors.joining(" | ")));
    }

    @Override
    public String getDescriptionText() {
        return strings.get("Version.description");
    }

    @Override
    protected List<String> getCommandAliases() {
        return COMMAND_ALIASES;
    }

}
