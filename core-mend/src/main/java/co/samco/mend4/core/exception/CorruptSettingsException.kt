package co.samco.mend4.core.exception

import co.samco.mend4.core.Settings

class CorruptSettingsException(message: String, setting: Settings.Name) :
    Exception(String.format(message, setting.encodedName))