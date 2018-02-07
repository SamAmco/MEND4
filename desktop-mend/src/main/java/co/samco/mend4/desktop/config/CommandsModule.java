package co.samco.mend4.desktop.config;

import co.samco.mend4.desktop.commands.*;
import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoSet;

@Module
public abstract class CommandsModule {
    @IntoSet @Binds abstract Command provideClean(Clean c);
    @IntoSet @Binds abstract Command provideVersion(Version c);
    @IntoSet @Binds abstract Command provideDecrypt(Decrypt c);
    @IntoSet @Binds abstract Command provideEncrypt(Encrypt c);
    @IntoSet @Binds abstract Command provideEncrypFromStdIn(EncryptFromStdIn c);
    @IntoSet @Binds abstract Command provideLock(Lock c);
    @IntoSet @Binds abstract Command provideMerge(Merge c);
    @IntoSet @Binds abstract Command provideSetProperty(SetProperty c);
    @IntoSet @Binds abstract Command provideSetupMend(SetupMend c);
    @IntoSet @Binds abstract Command provideStatePrinter(StatePrinter c);
    @IntoSet @Binds abstract Command provideUnlock(Unlock c);
    @IntoSet @Binds abstract Command provideHelp(Help c);

    @Binds abstract Command provideDefaultCommand(EncryptFromStdIn c);
}
