package co.samco.mend4.desktop.commands;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public abstract class Command {
    protected static final List<String> HELP_ALIASES = Arrays.asList("-h", "--help");

    protected abstract void execute(List<String> args);

    public void executeCommand(List<String> args) {
        if (Collections.disjoint(args, HELP_ALIASES)) {
            execute(args);
        } else {
            System.err.println(getUsageText());
        }
    }

    public boolean isCommandForString(String name) {
        return getCommandAliases().stream()
                .filter(s -> s.equals(name))
                .findFirst()
                .isPresent();
    }

    public abstract String getUsageText();

    public abstract String getDescriptionText();

    protected abstract List<String> getCommandAliases();

    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }

    @Override
    public int hashCode() {
        return getCommandAliases().stream()
                .collect(Collectors.joining())
                .hashCode();
    }

    @Override
    public String toString() {
        return getCommandAliases().stream()
                .collect(Collectors.joining(" | "));
    }
}
