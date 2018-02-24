package co.samco.mend4.desktop.config;

import co.samco.mend4.core.OSDao;
import co.samco.mend4.core.Settings;
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

@Module
public class DesktopModule {

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
