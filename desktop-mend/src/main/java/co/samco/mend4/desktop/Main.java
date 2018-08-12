package co.samco.mend4.desktop;

import co.samco.mend4.desktop.commands.Command;
import co.samco.mend4.desktop.commands.Help;
import co.samco.mend4.desktop.config.CommandsModule;
import co.samco.mend4.desktop.config.DesktopModule;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import dagger.Component;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class Main {
    private final I18N strings;
    private final PrintStreamProvider log;

    @Inject
    public Main(I18N strings, PrintStreamProvider log) {
        this.strings = strings;
        this.log = log;
    }

    public void run(Runner runner, List<String> args) throws Exception {
        if (args.size() < 1) {
            runner.defaultCommand().executeCommand(args);
        } else if (printHelp(runner, args)) {
           return;
        } else {
            Command command = findAndRunCommand(runner.commands(), args);
            if (command == null) {
                log.err().println(strings.get("Main.commandNotFound"));
                System.exit(1);
            } else if (command.getExecutionResult() != 0) {
                throw new Exception();
            }
        }
    }

    private boolean printHelp(Runner runner, List<String> args) {
        if (Help.HELP_ALIASES.contains(args.get(0))) {
            runner.helpCommand().executeCommand(args.subList(1, args.size()));
            return true;
        }
        return false;
    }

    private Command findAndRunCommand(Set<Command> commands, List<String> args) {
        for (Command c : commands) {
            if (c.isCommandForString(args.get(0))) {
                c.executeCommand(args.subList(1, args.size()));
                return c;
            }
        }
        return null;
    }

    @Singleton
    @Component (modules = { DesktopModule.class, CommandsModule.class })
    public interface Runner {
        Set<Command> commands();
        @Named(CommandsModule.HELP_COMMAND_NAME) Command helpCommand();
        @Named(CommandsModule.DEFAULT_COMMAND_NAME) Command defaultCommand();
        Main getMain();
    }

    public static void main(String[] args) throws Exception {
        Runner runner = DaggerMain_Runner.create();
        runner.getMain().run(runner, Arrays.asList(args));
    }
}

