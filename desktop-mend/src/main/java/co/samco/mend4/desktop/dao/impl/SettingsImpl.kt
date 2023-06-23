package co.samco.mend4.desktop.dao.impl

import co.samco.mend4.core.Settings
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

class SettingsImpl(
    private val settingsFile: File,
) : Settings {

    private var _propertiesCache: Properties? = null
    private var propertiesNotFound = false

    private val properties: Properties
        get() {
            if (_propertiesCache == null) {
                _propertiesCache = Properties()
                if (!settingsFile.exists()) propertiesNotFound = true
                else FileInputStream(settingsFile).use { _propertiesCache!!.loadFromXML(it) }
            }
            return _propertiesCache!!
        }

    private fun saveProperties(properties: Properties) {
        _propertiesCache = properties
        FileOutputStream(settingsFile).use { properties.storeToXML(it, "") }
    }

    override fun setValue(name: Settings.Name, value: String) {
        properties.setProperty(name.encodedName, value)
        saveProperties(properties)
    }

    override fun getValue(name: Settings.Name): String? {
        return if (propertiesNotFound) return null
        else properties.getProperty(name.encodedName)
    }
}