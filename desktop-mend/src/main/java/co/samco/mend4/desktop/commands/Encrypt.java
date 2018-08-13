package co.samco.mend4.desktop.commands;

import co.samco.mend4.core.Settings;
import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.exception.FileAlreadyExistsException;
import co.samco.mend4.desktop.exception.SettingRequiredException;
import co.samco.mend4.desktop.helper.CryptoHelper;
import co.samco.mend4.desktop.helper.FileResolveHelper;
import co.samco.mend4.desktop.helper.InputHelper;
import co.samco.mend4.desktop.helper.SettingsHelper;
import co.samco.mend4.desktop.input.InputListener;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class Encrypt extends Command implements InputListener {
    public static final String COMMAND_NAME = "enc";
    public static final String APPEND_FLAG = "-a";
    public static final String FROM_ARG_FLAG = "-d";

    protected final Settings settings;
    protected final SettingsHelper settingsHelper;
    protected final CryptoHelper cryptoHelper;
    protected final InputHelper inputHelper;
    protected final FileResolveHelper fileResolveHelper;
    protected final PrintStreamProvider log;
    protected final I18N strings;

    protected boolean dropHeader = false;

    private boolean waitingForInputListener = false;

    private final List<Function<List<String>, List<String>>> behaviourChain = Arrays.asList(
            a -> assertSettingsPresent(a),
            a -> shouldDropHeader(a),
            a -> shouldEncryptFromTextEditor(a),
            a -> shouldEncryptFromArg(a),
            a -> tooManyArgs(a),
            a -> encryptFile(a),
            a -> unknownBehaviour()
    );

    @Inject
    public Encrypt(Settings settings, SettingsHelper settingsHelper, PrintStreamProvider log, I18N strings,
                   CryptoHelper cryptoHelper, InputHelper inputHelper, FileResolveHelper fileResolveHelper) {
        this.settings = settings;
        this.settingsHelper = settingsHelper;
        this.log = log;
        this.cryptoHelper = cryptoHelper;
        this.inputHelper = inputHelper;
        this.fileResolveHelper = fileResolveHelper;
        this.strings = strings;
    }

    @Override
    public void execute(List<String> args) {
        executeBehaviourChain(behaviourChain, args);
        try {
            while (waitingForInputListener) {
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            failWithMessage(log, strings.get("Encrypt.threadInterrupt"));
        }
    }

    protected List<String> assertSettingsPresent(List<String> args) {
        try {
            settingsHelper.assertRequiredSettingsExist(new Settings.Name[]{
                        Settings.Name.PUBLICKEY, Settings.Name.PRIVATEKEY, Settings.Name.RSAKEYSIZE, Settings.Name.AESKEYSIZE,
                        Settings.Name.PREFERREDAES, Settings.Name.PREFERREDRSA, Settings.Name.ENCDIR, Settings.Name.LOGDIR},
                    COMMAND_NAME);
        } catch (IOException | SettingRequiredException e) {
            failWithMessage(log, e.getMessage());
            return null;
        }
        return args;
    }

    protected List<String> shouldDropHeader(List<String> args) {
        List<String> newArgs = new ArrayList<>(args);
        if (newArgs.contains(APPEND_FLAG)) {
            dropHeader = true;
            newArgs.remove(APPEND_FLAG);
        }
        return newArgs;
    }

    protected List<String> shouldEncryptFromTextEditor(List<String> args) {
        if (args.size() <= 0) {
            inputHelper.createInputProviderAndRegisterListener(this);
            waitingForInputListener = true;
            return null;
        }
        return args;
    }

    protected List<String> shouldEncryptFromArg(List<String> args) {
        if (args.contains(FROM_ARG_FLAG)) {
            int index = args.indexOf(FROM_ARG_FLAG);
            if (args.size() != index + 2) {
                log.err().println(strings.getf("Encrypt.badDataArgs", FROM_ARG_FLAG));
            } else {
                encryptTextToLog(args.get(index + 1).toCharArray());
            }
            return null;
        }
        return args;
    }

    protected List<String> tooManyArgs(List<String> args) {
        if (args.size() > 2) {
            log.err().println(strings.getf("General.invalidArgNum", COMMAND_NAME));
            return null;
        }
        return args;
    }

    protected List<String> encryptFile(List<String> args) {
        String name = null;
        if (args.size() > 1) {
            name = args.get(1);
        }
        try {
            cryptoHelper.encryptFile(fileResolveHelper.resolveFile(args.get(0)), name);
        } catch (IOException | CorruptSettingsException | InvalidKeySpecException | NoSuchAlgorithmException
                | IllegalBlockSizeException | InvalidKeyException | BadPaddingException | InvalidAlgorithmParameterException
                | NoSuchPaddingException | FileAlreadyExistsException e) {
            failWithMessage(log, e.getMessage());
        }
        return null;
    }

    protected List<String> unknownBehaviour() {
        log.err().println(strings.getf("Encrypt.malformedCommand", COMMAND_NAME));
        return null;
    }

    private void encryptTextToLog(char[] text) {
        try {
            cryptoHelper.encryptTextToLog(text, dropHeader);
        } catch (IOException | CorruptSettingsException | InvalidKeySpecException | NoSuchAlgorithmException
                | IllegalBlockSizeException | InvalidKeyException | BadPaddingException
                | InvalidAlgorithmParameterException | NoSuchPaddingException e) {
            failWithMessage(log, e.getMessage());
        }
    }

    @Override
    public String getUsageText() {
        return strings.getf("Encrypt.usage", COMMAND_NAME, APPEND_FLAG, FROM_ARG_FLAG);
    }

    @Override
    public String getDescriptionText() {
        return strings.get("Encrypt.description");
    }

    @Override
    protected List<String> getCommandAliases() {
        return Arrays.asList(COMMAND_NAME);
    }

    @Override
    public void onWrite(char[] text) {
        encryptTextToLog(text);
    }

    @Override
    public void onClose() {
        waitingForInputListener = false;
    }
}
