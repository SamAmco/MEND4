package co.samco.mend4.core.exception

class CorruptSettingsException(message: String?, setting: String?) :
    Exception(String.format(message!!, setting))