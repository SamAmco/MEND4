package co.samco.mendroid.model

import android.content.Context
import android.content.SharedPreferences
import co.samco.mend4.core.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

enum class Theme {
    LIGHT, DARK
}

interface PropertyManager : Settings {
    val selectedTheme: Flow<Theme?>
    val encDirUri: Flow<String?>
    val hasConfig: Flow<Boolean>
    val configUri: Flow<String?>
    val lockOnScreenLock: Flow<Boolean>

    fun setEncDirUri(uri: String)
    fun setTheme(theme: Theme?)
    fun getEncDirUri(): String?
    fun getSelectedTheme(): Theme?

    fun clearSettings()
    fun setConfigUriPath(path: String)

    fun getCurrentLogUri(): String?
    fun getLockOnScreenLock(): Boolean
    fun setCurrentLogUri(uriStr: String?)
    fun getKnownLogUris(): List<String>
    fun setKnownLogUris(uris: List<String>)
    fun setLockOnScreenLock(enabled: Boolean)
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
        private const val ENC_DIR_URI = "encDirUri"
        private const val CONFIG_URI = "configUri"
        private const val CURRENT_LOG_URI = "currentLogUri"
        private const val KNOWN_LOG_URIS = "knownLogUris"
        private const val THEME = "theme"
    }

    override val selectedTheme: Flow<Theme?>
        get() = onChange(THEME)
            .map { prefs -> prefs.getString(THEME, null)?.let { Theme.valueOf(it) } }

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

    override val lockOnScreenLock = MutableStateFlow(true)

    override fun getCurrentLogUri(): String? {
        return prefs.getString(CURRENT_LOG_URI, null)
    }

    override fun getLockOnScreenLock(): Boolean = lockOnScreenLock.value

    override fun setEncDirUri(uri: String) {
        //First remove it because if you select the same directory you still want the
        // onchange listener to fire
        prefs.edit().remove(ENC_DIR_URI).apply()
        prefs.edit().putString(ENC_DIR_URI, uri).apply()
    }

    override fun setTheme(theme: Theme?) {
        if (theme == null) prefs.edit().remove(THEME).apply()
        else prefs.edit().putString(THEME, theme.name).apply()
    }

    override fun getEncDirUri(): String? {
        return prefs.getString(ENC_DIR_URI, null)
    }

    override fun getSelectedTheme(): Theme? {
        return prefs.getString(THEME, null)?.let { Theme.valueOf(it) }
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

    override fun setCurrentLogUri(uriStr: String?) {
        if (uriStr == null) prefs.edit().remove(CURRENT_LOG_URI).apply()
        else prefs.edit().putString(CURRENT_LOG_URI, uriStr).apply()
    }

    override fun getKnownLogUris(): List<String> {
        return prefs.getStringSet(KNOWN_LOG_URIS, null)?.toList() ?: emptyList()
    }

    override fun setKnownLogUris(uris: List<String>) {
        prefs.edit().putStringSet(KNOWN_LOG_URIS, uris.toSet()).apply()
    }

    override fun setLockOnScreenLock(enabled: Boolean) {
        lockOnScreenLock.value = enabled
    }

    override fun setValue(name: Settings.Name, value: String) {
        prefs.edit().putString(name.encodedName, value).apply()
    }

    override fun getValue(name: Settings.Name): String? {
        return prefs.getString(name.encodedName, null)
    }
}