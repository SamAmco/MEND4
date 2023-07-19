package co.samco.mendroid

import android.content.Context
import android.content.SharedPreferences
import co.samco.mend4.core.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface PropertyManager : Settings {
    val logDirUri: Flow<String?>
    val encDirUri: Flow<String?>
    val hasConfig: Flow<Boolean>
    val configUri: Flow<String?>

    fun getCurrentLogName(): String

    fun setLogDirUri(uri: String)
    fun setEncDirUri(uri: String)
    fun getEncDirUri(): String?
    fun getLogDirUri(): String?

    fun clearSettings()
    fun setConfigUriPath(path: String)
    fun setCurrentLogName(name: String)
}

class PropertyManagerImpl @Inject constructor(
    @ApplicationContext context: Context
) : PropertyManager {

    private val prefs = context.getSharedPreferences(
        "${context.packageName}.UserPrefs",
        Context.MODE_PRIVATE
    )

    private fun onChange(key: String): Flow<SharedPreferences> {
        return callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, changedKey ->
                    if (key == changedKey) {
                        trySendBlocking(sharedPreferences)
                    }
                }

            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySendBlocking(prefs)
            awaitClose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
    }

    companion object {
        private const val LOG_DIR_URI = "logDirUri"
        private const val ENC_DIR_URI = "encDirUri"
        private const val CONFIG_URI = "configUri"
        private const val CURRENT_LOG_NAME = "currentLogName"
    }

    override val logDirUri: Flow<String?>
        get() = onChange(LOG_DIR_URI)
            .map { it.getString(LOG_DIR_URI, null) }

    override val encDirUri: Flow<String?>
        get() = onChange(ENC_DIR_URI)
            .map { it.getString(ENC_DIR_URI, null) }

    override val hasConfig: Flow<Boolean>
        get() = combine(
            Settings.Name.All.map { setting ->
                val name = setting.encodedName
                onChange(name).map { it.getString(name, null) }
            }
        ) { flows -> flows.all { it != null } }

    override val configUri: Flow<String?>
        get() = onChange(CONFIG_URI)
            .map { it.getString(CONFIG_URI, null) }

    override fun getCurrentLogName(): String {
        return prefs.getString(CURRENT_LOG_NAME, "Log") ?: "Log"
    }

    override fun setLogDirUri(uri: String) {
        //First remove it because if you select the same directory you still want the
        // onchange listener to fire
        prefs.edit().remove(LOG_DIR_URI).apply()
        prefs.edit().putString(LOG_DIR_URI, uri).apply()
    }

    override fun setEncDirUri(uri: String) {
        //First remove it because if you select the same directory you still want the
        // onchange listener to fire
        prefs.edit().remove(ENC_DIR_URI).apply()
        prefs.edit().putString(ENC_DIR_URI, uri).apply()
    }

    override fun getEncDirUri(): String? {
        return prefs.getString(ENC_DIR_URI, null)
    }

    override fun getLogDirUri(): String? {
        return prefs.getString(LOG_DIR_URI, null)
    }

    override fun clearSettings() {
        prefs.edit().apply {
            Settings.Name.All.forEach { remove(it.encodedName) }
            remove(CONFIG_URI)
        }.apply()
    }

    override fun setConfigUriPath(path: String) {
        prefs.edit().putString(CONFIG_URI, path).apply()
    }

    override fun setCurrentLogName(name: String) {
        prefs.edit().putString(CURRENT_LOG_NAME, name).apply()
    }

    override fun setValue(name: Settings.Name, value: String) {
        prefs.edit().putString(name.encodedName, value).apply()
    }

    override fun getValue(name: Settings.Name): String? {
        return prefs.getString(name.encodedName, null)
    }
}