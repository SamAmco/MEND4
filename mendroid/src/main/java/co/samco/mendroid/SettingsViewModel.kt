package co.samco.mendroid

import android.app.Application
import android.net.Uri
import android.util.Xml
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.samco.mend4.core.Settings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser
import java.io.FileInputStream
import java.io.InputStream
import javax.inject.Inject

data class ErrorToast(val messageId: Int, val formatArgs: List<String>)

@HiltViewModel
//Don't know why it thinks i'm leaking a context but it's application context so we're fine
class SettingsViewModel @Inject constructor(
    private val propertyManager: PropertyManager,
    application: Application
) : AndroidViewModel(application) {

    val configPath = propertyManager.configUri
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val logDirText = propertyManager.logDirUri
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val encDirText = propertyManager.encDirUri
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    data class ConfigImportException(val messageId: Int, val formatArgs: List<String>) : Exception()

    private val _errorToasts = MutableSharedFlow<ErrorToast>()
    val errorToasts: SharedFlow<ErrorToast> = _errorToasts

    val hasLogDir = propertyManager.logDirUri.map { it != null }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)
    val hasEncDir = propertyManager.encDirUri.map { it != null }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)
    val hasConfig = propertyManager.hasConfig
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val showSettings = combine(
        listOf(
            hasConfig,
            hasLogDir,
            hasEncDir
        )
    ) { list -> list.any { !it } }
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    private val context get() = this.getApplication<Application>().applicationContext

    private fun showErrorToast(messageId: Int, formatArgs: List<String>) {
        viewModelScope.launch {
            _errorToasts.emit(ErrorToast(messageId, formatArgs))
        }
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

    fun onSetLogDir(uri: Uri?) {

    }

    fun onSetEncDir(uri: Uri?) {

    }
}