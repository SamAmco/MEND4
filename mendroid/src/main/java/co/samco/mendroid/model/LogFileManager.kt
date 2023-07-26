package co.samco.mendroid.model

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import co.samco.mend4.core.AppProperties
import co.samco.mendroid.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

data class LogFileData(
    val name: String,
    val documentFile: DocumentFile,
    val uri: Uri
)

interface LogFileManager {
    val currentLog: Flow<LogFileData?>

    val knownLogs: Flow<List<LogFileData>>

    fun setCurrentLogFromUri(uri: Uri): Boolean

    fun setCurrentLog(data: LogFileData)
}

class LogFileManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val propertyManager: PropertyManager,
    private val errorToastManager: ErrorToastManager
) : LogFileManager, CoroutineScope {

    private val logFileExtension = ".${AppProperties.LOG_FILE_EXTENSION}"

    override val coroutineContext: CoroutineContext = Job() + Dispatchers.IO

    private val onLogCrudEvent = MutableSharedFlow<Unit>()

    private val logCrudEvents = onLogCrudEvent.onStart { emit(Unit) }

    override val currentLog: Flow<LogFileData?> = logCrudEvents
        .map {
            propertyManager.getCurrentLogUri()
                ?.let { getLogFileData(it) }
                ?: return@map null
        }
        .distinctUntilChanged()

    private val knownLogDataMap = logCrudEvents.map {
        propertyManager.getKnownLogUris().associateWith { getLogFileData(it) }
    }

    init {
        launch {
            knownLogDataMap.collect { storedLogs ->
                val size = storedLogs.size
                val knownLogData = storedLogs.values.filterNotNull()
                if (size != knownLogData.size) {
                    propertyManager.setKnownLogUris(knownLogData.map { it.uri.toString() })
                    onLogCrudEvent.emit(Unit)
                }
            }
        }
    }

    override val knownLogs: Flow<List<LogFileData>> = logCrudEvents.map {
        propertyManager.getKnownLogUris()
            .mapNotNull { getLogFileData(it) }
    }

    override fun setCurrentLogFromUri(uri: Uri): Boolean {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )

        val doc = DocumentFile.fromSingleUri(context, uri)
        if (doc == null || !doc.isFile || !doc.canRead() || !doc.canWrite()) {
            errorToastManager.showErrorToast(R.string.error_opening_log)
            return false
        }

        if (doc.name?.endsWith(logFileExtension) != true) {
            errorToastManager.showErrorToast(
                R.string.log_file_must_have_mend_extension,
                listOf(logFileExtension)
            )
            return false
        }

        propertyManager.setCurrentLogUri(uri.toString())

        val knownFiles = propertyManager.getKnownLogUris()
        if (!knownFiles.contains(uri.toString())) {
            propertyManager.setKnownLogUris(knownFiles + uri.toString())
        }

        launch { onLogCrudEvent.emit(Unit) }
        return true
    }

    override fun setCurrentLog(data: LogFileData) {
        propertyManager.setCurrentLogUri(data.uri.toString())
        launch { onLogCrudEvent.emit(Unit) }
    }

    private fun getLogFileData(uriStr: String?): LogFileData? {
        val documentFile = Uri.parse(uriStr)
            ?.let { DocumentFile.fromSingleUri(context, it) }
            ?.takeIf { it.isFile && it.canWrite() && it.canRead() }
            ?: return null

        val name = documentFile.name?.removeSuffix(logFileExtension) ?: return null

        return LogFileData(name, documentFile, documentFile.uri)
    }
}