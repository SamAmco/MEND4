package co.samco.mend4.desktop.helper.impl

import co.samco.mend4.core.Settings
import co.samco.mend4.core.exception.CorruptSettingsException
import co.samco.mend4.desktop.core.I18N
import co.samco.mend4.desktop.dao.SettingsDao
import co.samco.mend4.desktop.exception.SettingRequiredException
import co.samco.mend4.desktop.helper.SettingsHelper
import java.io.IOException
import javax.inject.Inject

class SettingsHelperImpl @Inject constructor(
    private val strings: I18N,
    private val settings: SettingsDao
) : SettingsHelper {

    @Throws(IOException::class, SettingRequiredException::class)
    override fun assertRequiredSettingsExist(required: Array<Settings.Name>, commandName: String) {
        settings.firstNonExistent(required)?.let {
            throw SettingRequiredException(
                strings.getf("General.unknownIdentifier", it, commandName)
            )
        }
    }

    override val settingDescriptions: String by lazy {
        SettingsDao.ALL_SETTINGS.joinToString(strings.newLine) {
            getFormattedSettingDescription(it.encodedName)
        }
    }

    override fun getSettingValueWrapped(name: Settings.Name): String {
        return try {
            settings.getValue(name) ?: strings["StatePrinter.notFound"]
        } catch (e: IOException) {
            strings["StatePrinter.Error"]
        } catch (e: CorruptSettingsException) {
            strings["StatePrinter.Error"]
        }
    }

    private fun getFormattedSettingDescription(name: String): String {
        return "\t" + name + "\t\t" + strings[SETTING_DESCRIPTION_PREFIX + name]
    }

    companion object {
        private const val SETTING_DESCRIPTION_PREFIX = "Settings.descriptions."
    }

}