package co.samco.mend4.desktop.commands;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.Settings;
import co.samco.mend4.desktop.core.DesktopSettings;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.*;

public class SubCommandRunner extends Command {

    private static Map<String, Class<?>> commands = new HashMap<>();

    static {
        commands.put("setup", SetupMend.class);
        commands.put("unlock", Unlock.class);
        commands.put("lock", Lock.class);
        commands.put("set", SetProperty.class);
        commands.put("enc", Encrypt.class);
        commands.put("enci", EncryptFromStdIn.class);
        commands.put("get", StatePrinter.class);
        commands.put("dec", Decrypt.class);
        commands.put("clean", Clean.class);
        commands.put("merge", Merge.class);
    }

    @Override
    public void execute(List<String> args) {
        try {
            Settings.InitializeSettings(new DesktopSettings());
            if (args.size() < 1) {
                Command c = (Command) commands.get("enci").newInstance();
                c.execute(new ArrayList<>());
                return;
            }

            if (args.get(0).equals("-v") || args.get(0).equals("--version")) {
                System.out.println("MEND core version " + Config.CORE_VERSION_NUMBER);
                System.out.println("MEND desktop version " + Settings.instance().getPlatformDependentHeader());
                return;
            }

            if (args.get(0).equals("-h") || args.get(0).equals("--help")) {
                System.err.println(getUsageText());
                return;
            }

            Iterator<Map.Entry<String, Class<?>>> it = commands.entrySet().iterator();
            boolean found = false;
            while (it.hasNext()) {
                Map.Entry<String, Class<?>> entry = it.next();
                if (entry.getKey().equals(args.get(0))) {
                    found = true;
                    ArrayList<String> arguments = new ArrayList<>();

                    for (int i = 1; i < args.size(); i++) {
                        arguments.add(args.get(i));
                    }

                    Command c = ((Command) entry.getValue().newInstance());
                    c.execute(arguments);
                }
            }

            if (!found) {
                System.err.println("Command not found.");
                System.err.println();
                System.err.println(getUsageText());
            }
        } catch (InstantiationException | IllegalAccessException | ParserConfigurationException
                | SAXException | IOException | Settings.UnInitializedSettingsException e) {
            System.err.println(e.getMessage());
        }
    }

    @Override
    public String getUsageText() {
        StringBuilder sb = new StringBuilder();
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
        return sb.toString();
    }

    @Override
    public String getDescriptionText() {
        return null;
    }
}




























