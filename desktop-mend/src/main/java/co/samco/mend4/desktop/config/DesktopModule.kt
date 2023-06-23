package co.samco.mend4.desktop.config

import co.samco.mend4.core.IBase64EncodingProvider
import co.samco.mend4.core.Settings
import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.core.crypto.impl.DefaultJCECryptoProvider
import co.samco.mend4.desktop.core.ApacheCommonsEncoder
import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.dao.impl.SettingsImpl
import co.samco.mend4.desktop.helper.FileResolveHelper
import co.samco.mend4.desktop.output.PrintStreamProvider
import co.samco.mend4.desktop.output.impl.SystemPrintStreamProviderImpl
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class DesktopModule {
    @Singleton
    @Provides
    fun provideCryptoProvider(
        log: PrintStreamProvider,
        settings: Settings,
        encoder: IBase64EncodingProvider
    ): CryptoProvider {
        return try {
            DefaultJCECryptoProvider(settings, encoder)
        } catch (t: Throwable) {
            log.err().println(t.message)
            throw RuntimeException(t.message)
        }
    }

    @Singleton
    @Provides
    fun provideIBase64EncodingProvider(): IBase64EncodingProvider {
        return ApacheCommonsEncoder()
    }

    @Singleton
    @Provides
    fun provideI18N(): I18N {
        return I18N("en", "UK")
    }

    @Singleton
    @Provides
    fun providePrintStreamProvider(): PrintStreamProvider {
        return SystemPrintStreamProviderImpl()
    }

    @Singleton
    @Provides
    fun provideSettings(fileResolveHelper: FileResolveHelper): Settings {
        return SettingsImpl(fileResolveHelper.settingsFile)
    }
}