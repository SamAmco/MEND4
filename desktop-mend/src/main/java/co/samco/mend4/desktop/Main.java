package co.samco.mend4.desktop;

import co.samco.mend4.desktop.commands.Command;
import co.samco.mend4.desktop.config.CommandsModule;
import co.samco.mend4.desktop.config.DesktopModule;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import dagger.Component;

import javax.inject.Inject;
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

    public void run(Runner runner, List<String> args) {
        if (args.size() < 1) {
            runner.defaultCommand().executeCommand(args);
        } else if (!findAndRunCommand(runner.commands(), args)) {
            log.err().println(strings.get("Main.commandNotFound"));
        }
    }

    private boolean findAndRunCommand(Set<Command> commands, List<String> args) {
        for (Command c : commands) {
            if (c.isCommandForString(args.get(0))) {
                c.executeCommand(args);
                return true;
            }
        }
        return false;
    }

    @Singleton
    @Component (modules = { DesktopModule.class, CommandsModule.class })
    interface Runner {
        Set<Command> commands();
        Command defaultCommand();
        Main getMain();
    }

    public static void main(String[] args) {
        Runner runner = DaggerMain_Runner.create();
        runner.getMain().run(runner, Arrays.asList(args));
    }
}

