package co.samco.mend4.desktop.commands;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;

//TODO implement this command somehow
public class Help extends Command {
    //NOTE: --help is explicitly mentioned in the strings
    List<String> COMMAND_ALIASES = Arrays.asList("-h", "--help");

    @Inject
    public Help() { }

    @Override
    protected void execute(List<String> args) { }

    @Override
    public String getUsageText() {
        /*StringBuilder sb = new StringBuilder();
        sb.append("Usage:\tmend [-v | -h] | [<command> [-h|<args>]]\n");
        sb.append("\n");
        sb.append("Commands:\n");
        Iterator<Map.Entry<String, Class<?>>> it = commands.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Class<?>> entry = it.next();
            sb.append("\t");
            sb.append(entry.getKey());
            sb.append("\t");
            //TODO this would need a try and catch, but we need to change it to spring anyway
            //sb.append(((Command) entry.getValue().newInstance()).getDescriptionText());
            System.err.println(sb.toString());
        }
        return sb.toString();*/
        return null;
    }

    @Override
    public String getDescriptionText() { return null; }

    @Override
    protected List<String> getCommandAliases() {
        return COMMAND_ALIASES;
    }
}
