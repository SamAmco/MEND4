@file:OptIn(FlowPreview::class)

package co.samco.mendroid.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Xml
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.samco.mend4.core.Settings
import co.samco.mendroid.model.ErrorToastManager
import co.samco.mendroid.model.PropertyManager
import co.samco.mendroid.R
import co.samco.mendroid.model.Theme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser
import java.io.FileInputStream
import java.io.InputStream
import javax.inject.Inject

@HiltViewModel
//Don't know why it thinks i'm leaking a context but it's application context so we're fine
class SettingsViewModel @Inject constructor(
    private val propertyManager: PropertyManager,
    application: Application,
    private val errorToastManager: ErrorToastManager
) : AndroidViewModel(application) {

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

    data class ConfigImportException(val messageId: Int, val formatArgs: List<String>) : Exception()

    private fun getDirectoryString(uriStr: String?): String? {
        if (uriStr == null) return null
        val uri = Uri.parse(uriStr)
        return uri.path ?: uri.toString()
    }

    val encDirGood = propertyManager.encDirUri
        .map { it != null && Uri.parse(it).assertValidDirectory() }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)
    val hasConfig = propertyManager.hasConfig
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    private val _userRequestShowSettings = MutableSharedFlow<Unit>()
    private val _userRequestCloseSettings = MutableSharedFlow<Unit>()

    private val forceShowSettings = combine(
        listOf(hasConfig, encDirGood)
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

        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            if (pfd.statSize < 0 || pfd.statSize > Integer.MAX_VALUE) return
            FileInputStream(pfd.fileDescriptor).use {
                try {
                    propertyManager.clearSettings()
                    tryParseXmlFile(it)
                    propertyManager.setConfigUriPath(uri.path ?: uri.toString())
                } catch (ex: ConfigImportException) {
                    showErrorToast(ex.messageId, ex.formatArgs)
                } catch (t: Throwable) {
                    showErrorToast(R.string.config_import_error, emptyList())
                }
            }
        }
    }

    private fun tryParseXmlFile(inputStream: InputStream) {
        val parser = Xml.newPullParser()
        val settings = Settings.Name.All.associateBy { it.encodedName }
        val found = mutableSetOf<String>()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)
        parser.nextTag()
        parser.require(XmlPullParser.START_TAG, null, "properties")
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.eventType != XmlPullParser.START_TAG) {
                    continue
                }
                val key = parser.getAttributeValue(null, "key")
                parser.next()
                if (parser.eventType != XmlPullParser.TEXT) {
                    throw ConfigImportException(
                        R.string.config_import_error_missing_text,
                        listOf(key)
                    )
                }
                val value = parser.text
                if (key in found) {
                    throw ConfigImportException(R.string.config_import_duplicate, listOf(key))
                }
                val setting = settings[key]
                if (setting != null) {
                    found.add(key)
                    propertyManager.setValue(setting, value)
                }
            }
        }
        if (found.size != settings.size) {
            val missing = (settings.keys - found).joinToString(", ")
            throw ConfigImportException(R.string.config_import_missing, listOf(missing))
        }
    }

    private fun Uri.assertValidDirectory(): Boolean {
        val documentFile = DocumentFile.fromTreeUri(context, this)

        if (documentFile == null || !documentFile.isDirectory
            || !documentFile.canRead() || !documentFile.canWrite()
        ) {
            return false
        }

        return true
    }

    private fun claimPersistentUriPermission(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }

    fun onSetEncDir(uri: Uri?) {
        if (uri == null || !uri.assertValidDirectory()) {
            showErrorToast(R.string.enc_dir_error, emptyList())
            return
        }
        claimPersistentUriPermission(uri)
        propertyManager.setEncDirUri(uri.toString())
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
}