package co.samco.mend4.desktop;

import co.samco.mend4.desktop.commands.SubCommandRunner;
import dagger.Component;
import java.util.Arrays;

public class Main {

    @Component
    interface Runner {
        SubCommandRunner runner();
    }

    public static void main(String[] args) {
        Runner runner = DaggerMain_Runner.create();
        runner.runner().execute(Arrays.asList(args));
    }
}







