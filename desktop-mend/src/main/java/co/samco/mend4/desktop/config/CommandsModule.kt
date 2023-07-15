package co.samco.mend4.desktop.config

import co.samco.mend4.desktop.commands.Clean
import co.samco.mend4.desktop.commands.Command
import co.samco.mend4.desktop.commands.Decrypt
import co.samco.mend4.desktop.commands.Encrypt
import co.samco.mend4.desktop.commands.EncryptFromStdIn
import co.samco.mend4.desktop.commands.Gcl
import co.samco.mend4.desktop.commands.Help
import co.samco.mend4.desktop.commands.Lock
import co.samco.mend4.desktop.commands.Merge
import co.samco.mend4.desktop.commands.Scl
import co.samco.mend4.desktop.commands.SetProperty
import co.samco.mend4.desktop.commands.Setup
import co.samco.mend4.desktop.commands.StatePrinter
import co.samco.mend4.desktop.commands.Unlock
import co.samco.mend4.desktop.commands.Version
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import javax.inject.Named

@Module
abstract class CommandsModule {
    @IntoSet
    @Binds
    abstract fun provideClean(c: Clean): Command

    @IntoSet
    @Binds
    abstract fun provideVersion(c: Version): Command

    @IntoSet
    @Binds
    abstract fun provideDecrypt(c: Decrypt): Command

    @IntoSet
    @Binds
    abstract fun provideEncrypt(c: Encrypt): Command

    @IntoSet
    @Binds
    abstract fun provideEncrypFromStdIn(c: EncryptFromStdIn): Command

    @IntoSet
    @Binds
    abstract fun provideLock(c: Lock): Command

    @IntoSet
    @Binds
    abstract fun provideMerge(c: Merge): Command

    @IntoSet
    @Binds
    abstract fun provideSetProperty(c: SetProperty): Command

    @IntoSet
    @Binds
    abstract fun provideSetupMend(c: Setup): Command

    @IntoSet
    @Binds
    abstract fun provideStatePrinter(c: StatePrinter): Command

    @IntoSet
    @Binds
    abstract fun provideUnlock(c: Unlock): Command

    @IntoSet
    @Binds
    abstract fun provideScl(c: Scl): Command

    @IntoSet
    @Binds
    abstract fun provideGcl(c: Gcl): Command

    @Binds
    @Named(HELP_COMMAND_NAME)
    abstract fun provideHelp(c: Help): Command

    @Binds
    @Named(DEFAULT_COMMAND_NAME)
    abstract fun provideDefaultCommand(c: EncryptFromStdIn): Command

    companion object {
        const val HELP_COMMAND_NAME = "help"
        const val DEFAULT_COMMAND_NAME = "default"
    }
}