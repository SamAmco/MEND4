package co.samco.mend4.desktop.commands;

import co.samco.mend4.desktop.output.PrintStreamProvider;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class Command {
    public static final List<String> HELP_ALIASES = Arrays.asList("-h", "--help");

    protected int executionResult = 0;

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

    protected void executeBehaviourChain(List<Function<List<String>, List<String>>> behaviourChain,
                                         List<String> args) {
        List<String> newArgs = args;
        for (Function<List<String>, List<String>> f : behaviourChain) {
            newArgs = f.apply(newArgs);
            if (newArgs == null) {
                return;
            }
        }
    }

    protected void failWithMessage(PrintStreamProvider log, String message) {
        log.err().println(message);
        executionResult = -1;
    }

    public int getExecutionResult() {
        return executionResult;
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
