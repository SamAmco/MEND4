package co.samco.mend4.core.exception

import co.samco.mend4.core.Settings
import java.lang.Exception

data class NoSuchSettingException(val missing: Settings.Name) : Exception()