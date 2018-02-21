package co.samco.mend4.desktop.config;

import co.samco.mend4.core.Settings;
import co.samco.mend4.core.impl.SettingsImpl;
import co.samco.mend4.desktop.core.I18N;
import co.samco.mend4.desktop.dao.OSDao;
import co.samco.mend4.desktop.dao.impl.OSDaoImpl;
import co.samco.mend4.desktop.helper.FileResolveHelper;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import co.samco.mend4.desktop.output.impl.SystemPrintStreamProviderImpl;
import dagger.Module;
import dagger.Provides;
import org.xml.sax.SAXException;

import javax.inject.Singleton;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;

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

    @Singleton @Provides Settings provideSettings(PrintStreamProvider log, FileResolveHelper fileResolveHelper) {
        try {
            return new SettingsImpl(new File(fileResolveHelper.getSettingsPath()));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            log.err().println(e.getMessage());
        }
        throw new RuntimeException("Settings failed to load. ");
    }
}
