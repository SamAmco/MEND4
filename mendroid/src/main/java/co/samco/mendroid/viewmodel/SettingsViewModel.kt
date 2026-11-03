@file:OptIn(FlowPreview::class)

package co.samco.mendroid.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Xml
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.samco.mend4.core.Settings
import co.samco.mendroid.R
import co.samco.mendroid.model.ErrorToastManager
import co.samco.mendroid.model.LocalLogDirectory
import co.samco.mendroid.model.LogFileManager
import co.samco.mendroid.model.PropertyManager
import co.samco.mendroid.model.Theme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser

@HiltViewModel
//Don't know why it thinks i'm leaking a context but it's application context so we're fine
class SettingsViewModel @Inject constructor(
    private val propertyManager: PropertyManager,
    application: Application,
    private val errorToastManager: ErrorToastManager,
    private val logs: LogFileManager
) : AndroidViewModel(application) {

    val lockOnScreenLock: StateFlow<Boolean> = propertyManager
        .lockOnScreenLock
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val selectedTheme: StateFlow<Theme?> = propertyManager.selectedTheme
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun onThemeClicked(selection: Theme) {
        val currentTheme = propertyManager.getSelectedTheme()
        propertyManager.setTheme(
            if (currentTheme == selection) null
            else selection
        )
    }

    val configPath = propertyManager.configUri
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val encDirText = propertyManager.encDirUri
        .map { getDirectoryString(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val logDirText = propertyManager.logDirUri
        .map { getDirectoryString(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val logDirGood = propertyManager.logDirUri
        .map { it != null && LocalLogDirectory.isLocalTree(it.toUri()) && it.toUri().assertValidDirectory() }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    data class ConfigImportException(val messageId: Int, val formatArgs: List<String>) : Exception()

    private fun getDirectoryString(uriStr: String?): String? {
        if (uriStr == null) return null
        val uri = uriStr.toUri()
        return uri.path ?: uri.toString()
    }

    val encDirGood = propertyManager.encDirUri
        .map { it != null && it.toUri().assertValidDirectory() }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Lazily, false)
    val hasConfig = propertyManager.hasConfig
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    private val _userRequestShowSettings = MutableSharedFlow<Unit>()
    private val _userRequestCloseSettings = MutableSharedFlow<Unit>()

    private val forceShowSettings = combine(
        listOf(hasConfig, encDirGood, logDirGood)
    ) { list -> list.any { !it } }
        .debounce(10)
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    private val userShowingSettings = combine(
        forceShowSettings,
        merge(
            _userRequestShowSettings.map { true },
            _userRequestCloseSettings.map { false }
        ).onStart { emit(false) }
    ) { force, show -> !force && show }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val showSettings = combine(
        forceShowSettings,
        userShowingSettings,
    ) { force, show -> force || show }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val showCloseButton = combine(
        forceShowSettings,
        userShowingSettings
    ) { force, show -> !force && show }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    private val context get() = this.getApplication<Application>().applicationContext

    private fun showErrorToast(messageId: Int, formatArgs: List<String>) {
        errorToastManager.showErrorToast(messageId, formatArgs)
    }

    fun onSetConfig(uri: Uri?) {
        if (uri == null) {
            showErrorToast(R.string.config_import_error, emptyList())
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settings = context.contentResolver.openInputStream(uri)?.use { tryParseXmlFile(it) }
                    ?: throw IOException("Config file is unavailable")
                propertyManager.clearSettings()
                settings.forEach { (name, value) -> propertyManager.setValue(name, value) }
                propertyManager.setConfigUriPath(uri.path ?: uri.toString())
            } catch (e: CancellationException) {
                throw e
            } catch (e: ConfigImportException) {
                showErrorToast(e.messageId, e.formatArgs)
            } catch (_: Exception) {
                showErrorToast(R.string.config_import_error, emptyList())
            }
        }
    }

    private fun tryParseXmlFile(inputStream: InputStream): Map<Settings.Name, String> {
        val parser = Xml.newPullParser()
        val settings = Settings.Name.All.associateBy { it.encodedName }
        val found = mutableMapOf<String, String>()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)
        parser.nextTag()
        parser.require(XmlPullParser.START_TAG, null, "properties")
        while (parser.nextTag() == XmlPullParser.START_TAG) {
            if (parser.name == "comment") {
                parser.nextText()
                continue
            }
            parser.require(XmlPullParser.START_TAG, null, "entry")
            val key = parser.getAttributeValue(null, "key")
                ?: throw ConfigImportException(R.string.config_import_error, emptyList())
            val value = parser.nextText()
            if (value.isEmpty()) {
                throw ConfigImportException(R.string.config_import_error_missing_text, listOf(key))
            }
            if (key in found) throw ConfigImportException(R.string.config_import_duplicate, listOf(key))
            if (key in settings) found[key] = value
        }
        parser.require(XmlPullParser.END_TAG, null, "properties")
        if (found.size != settings.size) {
            val missing = (settings.keys - found.keys).joinToString(", ")
            throw ConfigImportException(R.string.config_import_missing, listOf(missing))
        }
        return found.mapKeys { (key, _) -> settings.getValue(key) }
    }

    private fun Uri.assertValidDirectory(): Boolean = try {
        val directory = DocumentFile.fromTreeUri(context, this)
        directory != null && directory.isDirectory && directory.canRead() && directory.canWrite()
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun claimPersistentUriPermission(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }

    fun onSetEncDir(uri: Uri?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (uri == null || !uri.assertValidDirectory()) {
                    showErrorToast(R.string.enc_dir_error, emptyList())
                    return@launch
                }
                claimPersistentUriPermission(uri)
                propertyManager.setEncDirUri(uri.toString())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                showErrorToast(R.string.enc_dir_error, emptyList())
            }
        }
    }

    fun onSetLogDir(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!LocalLogDirectory.isLocalTree(uri) || !uri.assertValidDirectory()) {
                    showErrorToast(R.string.log_dir_error, emptyList())
                    return@launch
                }
                claimPersistentUriPermission(uri)
                propertyManager.setLogDirUri(uri.toString())
                logs.refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                showErrorToast(R.string.log_dir_error, emptyList())
            }
        }
    }

    fun onUserShowSettings() {
        viewModelScope.launch {
            _userRequestShowSettings.emit(Unit)
        }
    }

    fun onUserCloseSettings() {
        viewModelScope.launch {
            _userRequestCloseSettings.emit(Unit)
        }
    }

    fun onLockOnScreenLock(enabled: Boolean) {
        propertyManager.setLockOnScreenLock(enabled)
    }
}