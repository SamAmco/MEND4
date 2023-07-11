package co.samco.mend4.desktop.helper

import co.samco.mend4.core.Settings
import co.samco.mend4.desktop.exception.SettingRequiredException
import java.io.IOException

interface SettingsHelper {

    @Throws(IOException::class, SettingRequiredException::class)
    fun assertRequiredSettingsExist(required: Array<Settings.Name>, commandName: String)

    val settingDescriptions: String

    fun getSettingValueWrapped(name: Settings.Name): String
}