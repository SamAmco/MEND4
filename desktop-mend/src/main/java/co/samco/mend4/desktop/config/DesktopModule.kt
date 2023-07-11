package co.samco.mend4.desktop.config

import co.samco.mend4.core.IBase64EncodingProvider
import co.samco.mend4.core.Settings
import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.core.crypto.impl.DefaultJCECryptoProvider
import co.samco.mend4.desktop.core.ApacheCommonsEncoder
import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.core.I18NImpl
import co.samco.mend4.desktop.dao.OSDao
import co.samco.mend4.desktop.dao.SettingsDao
import co.samco.mend4.desktop.dao.impl.OSDaoImpl
import co.samco.mend4.desktop.dao.impl.SettingsImpl
import co.samco.mend4.desktop.helper.CryptoHelper
import co.samco.mend4.desktop.helper.FileResolveHelper
import co.samco.mend4.desktop.helper.InputHelper
import co.samco.mend4.desktop.helper.KeyHelper
import co.samco.mend4.desktop.helper.MergeHelper
import co.samco.mend4.desktop.helper.SettingsHelper
import co.samco.mend4.desktop.helper.ShredHelper
import co.samco.mend4.desktop.helper.VersionHelper
import co.samco.mend4.desktop.helper.impl.CryptoHelperImpl
import co.samco.mend4.desktop.helper.impl.FileResolveHelperImpl
import co.samco.mend4.desktop.helper.impl.InputHelperImpl
import co.samco.mend4.desktop.helper.impl.KeyHelperImpl
import co.samco.mend4.desktop.helper.impl.MergeHelperImpl
import co.samco.mend4.desktop.helper.impl.SettingsHelperImpl
import co.samco.mend4.desktop.helper.impl.ShredHelperImpl
import co.samco.mend4.desktop.helper.impl.VersionHelperImpl
import co.samco.mend4.desktop.output.ExitManager
import co.samco.mend4.desktop.output.PrintStreamProvider
import co.samco.mend4.desktop.output.impl.ExitManagerImpl
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
        settings: SettingsDao,
        encoder: IBase64EncodingProvider
    ): CryptoProvider {
        return try {
            DefaultJCECryptoProvider(settings, encoder)
        } catch (t: Throwable) {
            log.err().println(t.message)
            throw RuntimeException(t.message)
        }
    }

    @Provides
    fun provideExitManager(impl: ExitManagerImpl): ExitManager = impl

    @Provides
    fun provideCryptoHelper(impl: CryptoHelperImpl): CryptoHelper = impl

    @Provides
    fun provideFileResolveHelper(impl: FileResolveHelperImpl): FileResolveHelper = impl

    @Provides
    fun provideInputHelper(impl: InputHelperImpl): InputHelper = impl

    @Provides
    fun provideKeyHelper(impl: KeyHelperImpl): KeyHelper = impl

    @Provides
    fun provideMergeHelper(impl: MergeHelperImpl): MergeHelper = impl

    @Provides
    fun provideShredHelper(impl: ShredHelperImpl): ShredHelper = impl

    @Provides
    fun provideVersionHelper(impl: VersionHelperImpl): VersionHelper = impl

    @Provides
    fun getOSDao(impl: OSDaoImpl): OSDao = impl

    @Singleton
    @Provides
    fun provideIBase64EncodingProvider(): IBase64EncodingProvider = ApacheCommonsEncoder()

    @Singleton
    @Provides
    fun provideI18N(): I18N = I18NImpl("en", "UK")

    @Singleton
    @Provides
    fun providePrintStreamProvider(): PrintStreamProvider = SystemPrintStreamProviderImpl()

    @Singleton
    @Provides
    fun provideSettings(impl: SettingsImpl): SettingsDao = impl

    @Singleton
    @Provides
    fun provideSettingsHelper(impl: SettingsHelperImpl): SettingsHelper = impl
}