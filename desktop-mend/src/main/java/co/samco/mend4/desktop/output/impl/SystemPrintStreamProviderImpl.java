package co.samco.mend4.desktop.output.impl;

import co.samco.mend4.desktop.output.PrintStreamProvider;

import java.io.PrintStream;

public class SystemPrintStreamProviderImpl implements PrintStreamProvider {
    @Override
    public PrintStream err() {
        return System.err;
    }

    @Override
    public PrintStream out() {
        return System.out;
    }
}
