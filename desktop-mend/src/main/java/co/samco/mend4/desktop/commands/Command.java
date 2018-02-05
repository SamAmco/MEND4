package co.samco.mend4.desktop.commands;

import java.util.List;
import java.util.stream.Collectors;

public abstract class Command {
    public abstract void execute(List<String> args);

    protected boolean printHelp(List<String> args) {
        if (args.contains("-h")) {
            System.err.println(getUsageText());
            return true;
        }
        return false;
    }

    public boolean isCommandForString(String name) {
        return getCommandAliases().stream()
                .filter(s -> s.equals(name))
                .findFirst()
                .isPresent();
    }

    public abstract String getUsageText();

    public abstract String getDescriptionText();

    protected String[] generateShredCommandArgs(String fileName, String commandString) {
        String[] commandStrings = commandString.split(" ");
        for (int i = 0; i < commandStrings.length; ++i) {
            if (commandStrings[i].equals("<filename>"))
                commandStrings[i] = fileName;
        }
        return commandStrings;
    }

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
