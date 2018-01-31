package co.samco.mend4.desktop;

import co.samco.mend4.desktop.commands.Command;
import co.samco.mend4.desktop.config.AppConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;

@SpringBootApplication
public class Main implements CommandLineRunner {

    @Qualifier(AppConfig.INITIAL_COMMAND)
    @Autowired
    private Command initialCommand;

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        initialCommand.execute(Arrays.asList(args));
    }
}







