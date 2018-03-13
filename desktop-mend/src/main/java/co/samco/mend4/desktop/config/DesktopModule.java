package co.samco.mend4.desktop.config;

import co.samco.mend4.core.AppProperties;
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
import javax.swing.text.html.Option;
import java.io.File;
import java.io.IOException;
import java.util.Optional;

@Module
public class DesktopModule {

    @Singleton @Provides CryptoProvider provideCryptoProvider(PrintStreamProvider log, Settings settings) {
        try {
            return new DefaultJCECryptoProvider(AppProperties.STANDARD_IV,
                    Integer.parseInt(settings.getValue(Settings.Name.AESKEYSIZE)),
                    Integer.parseInt(settings.getValue(Settings.Name.RSAKEYSIZE)),
                    settings.getValue(Settings.Name.PREFERREDAES), settings.getValue(Settings.Name.PREFERREDRSA),
                    AppProperties.PASSCHECK_SALT, AppProperties.AES_KEY_GEN_ITERATIONS, new ApacheCommonsEncoder());
        } catch (IOException | CorruptSettingsException e) {
            log.err().println(e.getMessage());
            //TODO
            throw new RuntimeException("TODO");
        }
    }

    @Singleton @Provides I18N provideI18N() {
        return new I18N("en", "UK");
    }

    @Singleton @Provides PrintStreamProvider providePrintStreamProvider() {
        return new SystemPrintStreamProviderImpl();
    }

    @Singleton @Provides OSDao provideOSDao() {
        return new OSDaoImpl();
    }

    @Singleton @Provides Settings provideSettings(FileResolveHelper fileResolveHelper, OSDao osDao, I18N strings) {
        return new SettingsImpl(osDao, new File(fileResolveHelper.getSettingsPath()), strings.get("Settings.settingNotFound"));
    }
}
