package co.samco.mend4.desktop.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import javax.inject.Inject;

import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.helper.InputHelper;
import co.samco.mend4.desktop.input.InputListener;
import co.samco.mend4.desktop.helper.EncryptHelper;
import co.samco.mend4.desktop.input.InputProvider;
import co.samco.mend4.desktop.output.PrintStreamProvider;

public class Encrypt extends Command implements InputListener {
    public static final String COMMAND_NAME = "enc";
    public static final String APPEND_FLAG = "-a";
    public static final String FROM_ARG_FLAG = "-d";

    protected final EncryptHelper encryptHelper;
    protected final InputHelper inputHelper;
    protected final PrintStreamProvider log;
    protected final I18N strings;

    private InputProvider inputProvider;
    protected boolean dropHeader = false;

    private final List<Function<List<String>, List<String>>> behaviourChain = Arrays.asList(
            a -> shouldDropHeader(a),
            a -> shouldEncryptFromTextEditor(a),
            a -> shouldEncryptFromArg(a),
            a -> tooManyArgs(a),
            a -> encryptFile(a),
            a -> unknownBehaviour(a)
    );

    @Inject
    public Encrypt(PrintStreamProvider log, I18N strings, EncryptHelper encryptHelper, InputHelper inputHelper) {
        this.log = log;
        this.encryptHelper = encryptHelper;
        this.inputHelper = inputHelper;
        this.strings = strings;
    }

    @Override
    public void execute(List<String> args) {
        List<String> newArgs = args;
        for (Function<List<String>, List<String>> f : behaviourChain) {
            newArgs = f.apply(newArgs);
            if (newArgs == null) {
                return;
            }
        }
    }

    protected List<String> encryptFile(List<String> args) {
        String name = null;
        if (args.size() > 1) {
            name = args.get(1);
        }
        encryptHelper.encryptFile(args.get(0), name);
        return null;
    }

    protected List<String> unknownBehaviour(List<String> args) {
        log.err().println(strings.getf("Encrypt.malformedCommand", COMMAND_NAME));
        return null;
    }

    protected List<String> tooManyArgs(List<String> args) {
        if (args.size() > 2) {
            log.err().println(strings.getf("Encrypt.invalidArgNum", COMMAND_NAME));
            return null;
        }
        return args;
    }

    protected List<String> shouldEncryptFromTextEditor(List<String> args) {
        if (args.size() <= 0) {
            inputProvider = inputHelper.createInputProviderAndRegisterListener(this);
            return null;
        }
        return args;
    }

    protected List<String> shouldEncryptFromArg(List<String> args) {
        if (args.contains(FROM_ARG_FLAG)) {
            int index = args.indexOf(FROM_ARG_FLAG);
            if (args.size() != index + 2) {
                log.err().println(strings.getf("Encrypt.badDataArgs", FROM_ARG_FLAG));
            }
            encryptHelper.encryptTextToLog(args.get(index + 1).toCharArray(), dropHeader);
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
    public void onLogAndClose(char[] text) {
        inputProvider.clear();
        encryptHelper.encryptTextToLog(text, dropHeader);
        inputProvider.close();
    }

    @Override
    public void onLogAndClear(char[] text) {
        inputProvider.clear();
        encryptHelper.encryptTextToLog(text, dropHeader);
    }
}
