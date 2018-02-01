package co.samco.mend4.desktop.commands;

import java.util.List;

public abstract class Command {
    public abstract void execute(List<String> args);

    protected boolean printHelp(List<String> args) {
        if (args.contains("-h")) {
            System.err.println(getUsageText());
            return true;
        }
        return false;
    }

    public abstract boolean isCommandForString(String name);

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

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();

    @Override
    public abstract String toString();
}
