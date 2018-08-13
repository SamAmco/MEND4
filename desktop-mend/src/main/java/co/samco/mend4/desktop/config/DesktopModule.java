package co.samco.mend4.desktop.config;

import co.samco.mend4.core.AppProperties;
import co.samco.mend4.core.IBase64EncodingProvider;
import co.samco.mend4.core.OSDao;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.crypto.CryptoProvider;
import co.samco.mend4.core.crypto.impl.DefaultJCECryptoProvider;
import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.desktop.core.ApacheCommonsEncoder;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.dao.impl.OSDaoImpl;
import co.samco.mend4.desktop.dao.impl.SettingsImpl;
import co.samco.mend4.desktop.helper.FileResolveHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import co.samco.mend4.desktop.output.impl.SystemPrintStreamProviderImpl;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;

@Module
public class DesktopModule {

    @Singleton @Provides CryptoProvider provideCryptoProvider(PrintStreamProvider log, Settings settings,
                                                              IBase64EncodingProvider encoder) {
        try {
            int aesKeySize = AppProperties.PREFERRED_AES_KEY_SIZE;
            int rsaKeySize = AppProperties.PREFERRED_RSA_KEY_SIZE;
            String preferredAES = AppProperties.PREFERRED_AES_ALG;
            String preferredRSA = AppProperties.PREFERRED_RSA_ALG;
            if (settings.valueSet(Settings.Name.AESKEYSIZE)) {
                aesKeySize = Integer.parseInt(settings.getValue(Settings.Name.AESKEYSIZE));
            }
            if (settings.valueSet(Settings.Name.RSAKEYSIZE)) {
                rsaKeySize = Integer.parseInt(settings.getValue(Settings.Name.RSAKEYSIZE));
            }
            if (settings.valueSet(Settings.Name.PREFERREDAES)) {
                preferredAES = settings.getValue(Settings.Name.PREFERREDAES);
            }
            if (settings.valueSet(Settings.Name.PREFERREDRSA)) {
                preferredRSA = settings.getValue(Settings.Name.PREFERREDRSA);
            }

            return new DefaultJCECryptoProvider(AppProperties.STANDARD_IV,
                    aesKeySize, rsaKeySize, preferredAES, preferredRSA,
                    AppProperties.PASSCHECK_SALT, AppProperties.AES_KEY_GEN_ITERATIONS, encoder);
        } catch (IOException | CorruptSettingsException e) {
            log.err().println(e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    @Singleton @Provides IBase64EncodingProvider provideIBase64EncodingProvider() {
        return new ApacheCommonsEncoder();
    }


    @Singleton @Provides I18N provideI18N() {
        return new I18N("en", "UK");
    }

    @Singleton @Provides PrintStreamProvider providePrintStreamProvider() {
        return new SystemPrintStreamProviderImpl();
    }

    @Singleton @Provides OSDao provideOSDao(I18N strings) {
        return new OSDaoImpl(strings);
    }

    @Singleton @Provides Settings provideSettings(FileResolveHelper fileResolveHelper, OSDao osDao, I18N strings) {
        return new SettingsImpl(osDao, new File(fileResolveHelper.getSettingsFilePath()), strings.get("Settings.settingNotFound"));
    }
}
