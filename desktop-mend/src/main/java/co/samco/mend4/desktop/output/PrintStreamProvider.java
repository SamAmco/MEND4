package co.samco.mend4.desktop.output;

import java.io.PrintStream;

public interface PrintStreamProvider {
    PrintStream err();
    PrintStream out();
}
