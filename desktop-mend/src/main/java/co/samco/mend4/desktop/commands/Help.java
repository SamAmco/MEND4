package co.samco.mend4.desktop.commands;

import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.output.PrintStreamProvider;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Help extends Command {
    public static final String HELP_FLAG = "-h";
    List<String> COMMAND_ALIASES = Arrays.asList(HELP_FLAG, "--help");

    private final Set<Command> commands;
    private final I18N strings;
    private final PrintStreamProvider log;

    @Inject
    public Help(I18N strings, PrintStreamProvider log, Set<Command> commands) {
        this.strings = strings;
        this.log = log;
        this.commands = commands;
    }

    @Override
    protected void execute(List<String> args) {
        log.err().println(getUsageText());
    }

    @Override
    public String getUsageText() {
        StringBuilder sb = new StringBuilder();
        appendMendUsage(sb);
        appendCommandDescriptions(sb);
        return sb.toString();
    }

    private void appendCommandDescriptions(StringBuilder sb) {
        sb.append(strings.get("Help.commands"));
        sb.append(strings.getNewLine());
        Iterator<Command> it = commands.iterator();
        while (it.hasNext()) {
            Command entry = it.next();
            sb.append("\t");
            appendCommandDescription(sb, entry);
            sb.append(strings.getNewLine());
        }
    }

    private void appendCommandDescription(StringBuilder sb, Command command) {
        sb.append(command.getCommandAliases().stream().collect(Collectors.joining(", ")));
        sb.append("\t:\t");
        sb.append(command.getDescriptionText());
    }

    private void appendMendUsage(StringBuilder sb) {
        sb.append(strings.getf("Help.usage", Version.COMMAND_NAME, HELP_FLAG, HELP_FLAG));
        sb.append(strings.getNewLine());
        sb.append(strings.getNewLine());
    }

    @Override
    public String getDescriptionText() {
        return strings.get("Help.description");
    }

    @Override
    protected List<String> getCommandAliases() {
        return COMMAND_ALIASES;
    }
}





















