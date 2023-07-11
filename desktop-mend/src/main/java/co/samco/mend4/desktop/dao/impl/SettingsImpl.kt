package co.samco.mend4.desktop.dao.impl

import co.samco.mend4.core.Settings
import co.samco.mend4.desktop.dao.OSDao
import co.samco.mend4.desktop.dao.SettingsDao
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties
import javax.inject.Inject

class SettingsImpl @Inject constructor(osDao: OSDao) : SettingsDao {
    private val settingsFile = osDao.getSettingsFile()

    private var _propertiesCache: Properties? = null

    private val properties: Properties
        get() {
            if (_propertiesCache == null) {
                _propertiesCache = Properties()
                if (settingsFile.exists()) FileInputStream(settingsFile).use {
                    _propertiesCache!!.loadFromXML(it)
                }
            }
            return _propertiesCache!!
        }


    private fun saveProperties(properties: Properties) {
        _propertiesCache = properties
        FileOutputStream(settingsFile).use { properties.storeToXML(it, "") }
    }

    override fun firstNonExistent(required: Array<Settings.Name>): Settings.Name? =
        required.firstOrNull { properties.getProperty(it.encodedName) == null }

    override fun setValue(name: Settings.Name, value: String) {
        properties.setProperty(name.encodedName, value)
        saveProperties(properties)
    }

    override fun getValue(name: Settings.Name): String? =
        properties.getProperty(name.encodedName)
}