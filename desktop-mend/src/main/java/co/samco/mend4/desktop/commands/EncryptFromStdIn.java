package co.samco.mend4.desktop.commands;

import co.samco.mend4.core.OSDao;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.helper.CryptoHelper;
import co.samco.mend4.desktop.helper.FileResolveHelper;
import co.samco.mend4.desktop.helper.InputHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.inject.Inject;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.function.Function;

public class EncryptFromStdIn extends Encrypt {
    public static final String COMMAND_NAME = "enci";

    private OSDao osDao;

    private final List<Function<List<String>, List<String>>> behaviourChain = Arrays.asList(
            a -> assertSettingsPresent(a),
            a -> shouldDropHeader(a),
            a -> encryptFromInput(a)
    );

    @Inject
    public EncryptFromStdIn(Settings settings, PrintStreamProvider log, I18N strings, CryptoHelper cryptoHelper,
                            InputHelper inputHelper, OSDao osDao, FileResolveHelper fileResolveHelper) {
        super(settings, log, strings, cryptoHelper, inputHelper, fileResolveHelper);
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

        try {
            cryptoHelper.encryptTextToLog(sb.toString().toCharArray(), dropHeader);
        } catch (IOException | CorruptSettingsException | InvalidKeySpecException | NoSuchAlgorithmException
                | IllegalBlockSizeException | InvalidKeyException | BadPaddingException
                | InvalidAlgorithmParameterException | NoSuchPaddingException e) {
            e.printStackTrace();
        }
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
