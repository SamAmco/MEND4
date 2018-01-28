package co.samco.mend4.desktop.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.parsers.ParserConfigurationException;

import co.samco.mend4.desktop.commands.*;
import org.xml.sax.SAXException;

import co.samco.mend4.core.Config;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.Settings.UnInitializedSettingsException;

public class Main {
    private static Map<String, Class<?>> commands = new HashMap<String, Class<?>>();

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

    public static void main(String[] args) {
        try {
            Settings.InitializeSettings(new DesktopSettings());
            if (args.length < 1) {
                Command c = (Command) commands.get("enci").newInstance();
                c.execute(new ArrayList<String>());
                return;
            }

            if (args[0].equals("-v") || args[0].equals("--version")) {
                System.out.println("MEND core version " + Config.CORE_VERSION_NUMBER);
                System.out.println("MEND desktop version " + Settings.instance().getPlatformDependentHeader());
                return;
            }

            if (args[0].equals("-h") || args[0].equals("--help")) {
                printUsage();
                return;
            }

            Iterator<Entry<String, Class<?>>> it = commands.entrySet().iterator();
            boolean found = false;
            while (it.hasNext()) {
                Entry<String, Class<?>> entry = it.next();
                if (entry.getKey().equals(args[0])) {
                    found = true;
                    ArrayList<String> arguments = new ArrayList<String>();

                    for (int i = 1; i < args.length; i++) {
                        arguments.add(args[i]);
                    }

                    Command c = ((Command) entry.getValue().newInstance());
                    c.execute(arguments);
                }
            }

            if (!found) {
                System.err.println("Command not found.");
                System.err.println();
                printUsage();
            }
        } catch (InstantiationException | IllegalAccessException | ParserConfigurationException
                | SAXException | IOException | UnInitializedSettingsException e) {
            System.err.println(e.getMessage());
        }
    }

    private static void printUsage()
            throws InstantiationException, IllegalAccessException {
        System.err.println("Usage:\tmend [-v | -h] | [<command> [-h|<args>]]");
        System.err.println();
        System.err.println("Commands:");
        Iterator<Entry<String, Class<?>>> it = commands.entrySet().iterator();
        while (it.hasNext()) {
            Entry<String, Class<?>> entry = it.next();
            StringBuilder sb = new StringBuilder();
            sb.append("\t");
            sb.append(entry.getKey());
            sb.append("\t");
            sb.append(((Command) entry.getValue().newInstance()).getDescriptionText());
            System.err.println(sb.toString());
        }
    }
}







