import co.samco.mend4.desktop.Main;
import co.samco.mend4.desktop.commands.Command;
import co.samco.mend4.desktop.commands.Help;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.PrintStream;
import java.util.*;

import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class MainTest {
    private Main main;
    private I18N strings;
    private PrintStreamProvider log;
    private PrintStream err;
    private PrintStream out;
    private Main.Runner runner;

    @Captor
    private ArgumentCaptor<List<String>> argsCaptor;

    @Before
    public void setup() {
        strings = new I18N("en", "UK");
        runner = mock(Main.Runner.class);
        err = mock(PrintStream.class);
        out = mock(PrintStream.class);
        log = mock(PrintStreamProvider.class);
        when(log.err()).thenReturn(err);
        when(log.out()).thenReturn(out);
        main = new Main(strings, log);
    }

    @Test
    public void helpTest() throws Exception {
        Help help = mock(Help.class);
        when(runner.helpCommand()).thenReturn(help);
        for (String s : Help.HELP_ALIASES) {
            main.run(runner, Arrays.asList(s));
        }
        verify(runner, times(Help.HELP_ALIASES.size())).helpCommand();
        verify(help, times(Help.HELP_ALIASES.size())).executeCommand(argsCaptor.capture());
        Assert.assertTrue(argsCaptor.getValue().size() == 0);
    }

    @Test
    public void noArgs() throws Exception {
        Command defaultCommand = mock(Command.class);
        when(runner.defaultCommand()).thenReturn(defaultCommand);
        main.run(runner, Collections.emptyList());
        verify(runner).defaultCommand();
        verify(defaultCommand).executeCommand(anyListOf(String.class));
    }

    @Test
    public void runCommandWithArgs() throws Exception {
        Command command = mock(Command.class);
        runCommand(Arrays.asList("hi"), command);
    }

    @Test
    public void runCommand() throws Exception {
        Command command = mock(Command.class);
        runCommand(Collections.emptyList(), command);
    }

    @Test (expected = Exception.class)
    public void commandFailed() throws Exception {
        Command command = mock(Command.class);
        when(command.getExecutionResult()).thenReturn(-1);
        runCommand(Collections.emptyList(), command);
    }

    private void runCommand(List<String> subCommandArgs, Command command) throws Exception {
        String commandName = "command";
        when(command.isCommandForString(commandName)).thenReturn(true);
        Set<Command> commands = new HashSet<>();
        commands.add(command);
        when(runner.commands()).thenReturn(commands);
        List<String> args = new ArrayList<>();
        args.add(commandName);
        args.addAll(subCommandArgs);
        main.run(runner, args);
        verify(runner).commands();
        verify(command).isCommandForString(commandName);
        verify(command).executeCommand(argsCaptor.capture());
        Assert.assertThat(argsCaptor.getValue(), is(subCommandArgs));
    }
}



































