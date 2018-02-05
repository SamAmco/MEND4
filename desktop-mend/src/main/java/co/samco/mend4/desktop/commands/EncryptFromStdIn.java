package co.samco.mend4.desktop.commands;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class EncryptFromStdIn extends Encrypt {
    private final String COMMAND_NAME = "enci";

    @Inject
    public EncryptFromStdIn() { }

    @Override
    public void execute(List<String> args) {
        if (printHelp(args))
            return;

        readOptions(args);

        Scanner scanner = new Scanner(System.in);
        StringBuilder sb = new StringBuilder();

        while (scanner.hasNextLine()) {
            sb.append(scanner.nextLine());
            sb.append(System.getProperty("line.separator"));
        }
        scanner.close();

        encryptTextToLog(sb.toString().toCharArray(), dropHeader);
    }

    @Override
    public List<String> getCommandAliases() {
        return Arrays.asList(COMMAND_NAME);
    }

    @Override
    public String getUsageText() {
        return "Usage:\tmend enci [-a]";
    }

    @Override
    public String getDescriptionText() {
        return "To encrypt text to your current log from stdin.";
    }
}
