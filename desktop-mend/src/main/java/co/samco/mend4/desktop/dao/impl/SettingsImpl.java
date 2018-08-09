package co.samco.mend4.desktop.dao.impl;

import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.core.OSDao;
import co.samco.mend4.core.Settings;

import java.io.*;
import java.util.Properties;

public class SettingsImpl implements Settings {
    private final OSDao osDao;
    private final File settingsFile;
    private Properties _propertiesCache;
    private final String corruptSettingsExceptionText;

    public SettingsImpl(OSDao osDao, File settingsFile, String corruptSettingsExceptionText) {
        this.osDao = osDao;
        this.settingsFile = settingsFile;
        this.corruptSettingsExceptionText = corruptSettingsExceptionText;
    }

    private Properties getProperties() throws IOException {
        if (_propertiesCache == null) {
            try (InputStream fis = osDao.getInputStreamForFile(settingsFile)) {
                _propertiesCache.loadFromXML(fis);
            } catch (IOException e) {
                throw e;
            }
        }
        return _propertiesCache;
    }

    private void saveProperties(Properties properties) throws IOException {
        _propertiesCache = properties;
        try (OutputStream fos = osDao.getOutputStreamForFile(settingsFile)) {
            properties.storeToXML(fos, "");
        } catch (IOException e) {
            throw e;
        }
    }

    @Override
    public void setValue(Name name, String value) throws IOException {
        Properties properties = getProperties();
        properties.setProperty(name.toString(), value);
        saveProperties(properties);
    }

    @Override
    public boolean valueSet(Name name) throws IOException {
        Properties properties = getProperties();
        String value = properties.getProperty(name.toString());
        return value != null;
    }

    @Override
    public String getValue(Name name) throws IOException, CorruptSettingsException {
        Properties properties = getProperties();
        String value = properties.getProperty(name.toString());
        if (value == null) {
            throw new CorruptSettingsException(corruptSettingsExceptionText, name.toString());
        }
        return value;
    }
}
