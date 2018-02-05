package co.samco.mend4.desktop.commands;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;

public class Version extends Command {
    //TODO we may want to have many aliases for this command
    private final List<String> COMMAND_ALIASES = Arrays.asList("-v", "--version");

    @Inject
    public Version() {}

    @Override
    public void execute(List<String> args) {
        System.err.println("YOYOYO");
    }

    @Override
    public String getUsageText() {
        return null;
    }

    @Override
    public String getDescriptionText() {
        return null;
    }

    @Override
    protected List<String> getCommandAliases() {
        return COMMAND_ALIASES;
    }

}
