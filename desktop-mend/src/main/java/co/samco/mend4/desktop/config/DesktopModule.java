package co.samco.mend4.desktop.config;

import co.samco.mend4.core.Settings;
import co.samco.mend4.desktop.core.DesktopSettingsImpl;
import co.samco.mend4.desktop.dao.OSDao;
import co.samco.mend4.desktop.dao.impl.OSDaoImpl;
import co.samco.mend4.desktop.output.PrintStreamProvider;
import co.samco.mend4.desktop.output.impl.SystemPrintStreamProviderImpl;
import dagger.Module;
import dagger.Provides;
import org.xml.sax.SAXException;

import javax.inject.Singleton;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

@Module
public class DesktopModule {

    @Singleton @Provides PrintStreamProvider providePrintStreamProvider() {
        return new SystemPrintStreamProviderImpl();
    }

    @Singleton @Provides OSDao provideOSDao() {
        return new OSDaoImpl();
    }

    @Singleton @Provides Settings provideSettings() {
        try {
            return new DesktopSettingsImpl();
        }
        catch (ParserConfigurationException | SAXException | IOException e) {
            System.err.println(e.getMessage());
        }
        throw new RuntimeException("Settings failed to load. ");
    }
}
