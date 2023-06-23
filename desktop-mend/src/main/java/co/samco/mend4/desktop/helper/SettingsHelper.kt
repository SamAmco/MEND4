package co.samco.mend4.desktop.helper

import co.samco.mend4.core.Settings
import co.samco.mend4.core.exception.CorruptSettingsException
import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.exception.SettingRequiredException
import java.io.IOException
import javax.inject.Inject

class SettingsHelper @Inject constructor(
    private val strings: I18N,
    private val settings: Settings
) {
    @Throws(IOException::class, SettingRequiredException::class)
    fun assertRequiredSettingsExist(required: Array<Settings.Name>, commandName: String) {
        required.firstOrNull { settings.getValue(it) == null }?.let {
            throw SettingRequiredException(
                strings.getf(
                    "General.unknownIdentifier",
                    it.encodedName,
                    commandName
                )
            )
        }
    }

    fun settingExists(name: String): Boolean {
        return Settings.Name.values().any { it.encodedName == name }
    }

    val settingDescriptions: String
        get() = Settings.Name.values()
            .joinToString(strings.newLine) { getFormattedSettingDescription(it) }

    fun getSettingValueWrapped(name: Settings.Name): String {
        return try {
            settings.getValue(name) ?: strings["StatePrinter.notFound"]
        } catch (e: IOException) {
            strings["StatePrinter.Error"]
        } catch (e: CorruptSettingsException) {
            strings["StatePrinter.Error"]
        }
    }

    private fun getFormattedSettingDescription(name: Settings.Name): String {
        return "\t" + name + "\t\t" + strings[SETTING_DESCRIPTION_PREFIX + name.toString()]
    }

    companion object {
        private const val SETTING_DESCRIPTION_PREFIX = "Settings.descriptions"
    }
}