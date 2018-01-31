package co.samco.mend4.desktop.config;

import co.samco.mend4.desktop.commands.Command;
import co.samco.mend4.desktop.commands.SubCommandRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {
    public final static String INITIAL_COMMAND = "InitialCommand";

    @Bean(INITIAL_COMMAND)
    public Command getInitialCommand() {
        return new SubCommandRunner();
    }

}
