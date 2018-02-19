package co.samco.mend4.desktop.config;

import co.samco.mend4.desktop.commands.*;
import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoSet;

import javax.inject.Named;

@Module
public abstract class CommandsModule {
    public static final String HELP_COMMAND_NAME = "help";
    public static final String DEFAULT_COMMAND_NAME = "default";

    @IntoSet @Binds abstract Command provideClean(Clean c);
    @IntoSet @Binds abstract Command provideVersion(Version c);
    @IntoSet @Binds abstract Command provideDecrypt(Decrypt c);
    @IntoSet @Binds abstract Command provideEncrypt(Encrypt c);
    @IntoSet @Binds abstract Command provideEncrypFromStdIn(EncryptFromStdIn c);
    @IntoSet @Binds abstract Command provideLock(Lock c);
    @IntoSet @Binds abstract Command provideMerge(Merge c);
    @IntoSet @Binds abstract Command provideSetProperty(SetProperty c);
    @IntoSet @Binds abstract Command provideSetupMend(Setup c);
    @IntoSet @Binds abstract Command provideStatePrinter(StatePrinter c);
    @IntoSet @Binds abstract Command provideUnlock(Unlock c);

    @Binds @Named(HELP_COMMAND_NAME) abstract Command provideHelp(Help c);
    @Binds @Named(DEFAULT_COMMAND_NAME) abstract Command provideDefaultCommand(EncryptFromStdIn c);
}
