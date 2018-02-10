package co.samco.mend4.desktop.commands;

import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.dao.OSDao;
import co.samco.mend4.desktop.helper.CryptoHelper;
import co.samco.mend4.desktop.helper.InputHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.function.Function;

public class EncryptFromStdIn extends Encrypt {
    public static final String COMMAND_NAME = "enci";

    private OSDao osDao;

    private final List<Function<List<String>, List<String>>> behaviourChain = Arrays.asList(
            a -> shouldDropHeader(a),
            a -> encryptFromInput(a)
    );

    @Inject
    public EncryptFromStdIn(PrintStreamProvider log, I18N strings, CryptoHelper cryptoHelper,
                            InputHelper inputHelper, OSDao osDao) {
        super(log, strings, cryptoHelper, inputHelper);
        this.osDao = osDao;
    }

    private List<String> encryptFromInput(List<String> args) {
        Scanner scanner = new Scanner(osDao.getStdIn());
        StringBuilder sb = new StringBuilder();

        while (scanner.hasNextLine()) {
            sb.append(scanner.nextLine());
            if (scanner.hasNextLine()) {
                sb.append(strings.getNewLine());
            }
        }
        scanner.close();

        cryptoHelper.encryptTextToLog(sb.toString().toCharArray(), dropHeader);
        return null;
    }

    @Override
    public void execute(List<String> args) {
        executeBehaviourChain(behaviourChain, args);
    }

    @Override
    public List<String> getCommandAliases() {
        return Arrays.asList(COMMAND_NAME);
    }

    @Override
    public String getUsageText() {
        return strings.getf("EncryptFromStdIn.usage", COMMAND_NAME, APPEND_FLAG);
    }

    @Override
    public String getDescriptionText() {
        return strings.get("EncryptFromStdIn.description");
    }
}
