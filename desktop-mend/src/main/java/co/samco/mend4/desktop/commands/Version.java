package co.samco.mend4.desktop.commands;

import javax.inject.Inject;
import java.util.List;

public class Version extends Command {
    //TODO we may want to have many aliases for this command
    private final String COMMAND_NAME = "-v";

    @Inject
    public Version() {}

    @Override
    public void execute(List<String> args) {
        System.err.println("YOYOYO");
    }

    @Override
    public boolean isCommandForString(String name) {
        return name.equals(COMMAND_NAME);
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
    public boolean equals(Object obj) {
        return obj == this;
    }

    @Override
    public int hashCode() {
        return Integer.valueOf(COMMAND_NAME);
    }

    @Override
    public String toString() {
        return COMMAND_NAME;
    }
}
