@file:OptIn(ExperimentalCoroutinesApi::class)

package co.samco.mendroid.viewmodel

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import co.samco.mend4.core.AppProperties
import co.samco.mendroid.model.FileEventManager
import co.samco.mendroid.model.PropertyManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

data class LogFileData(
    val name: String,
    val uri: Uri
)

class FileHelper(
    private val context: Context,
    private val propertyManager: PropertyManager,
    private val fileEventManager: FileEventManager
) {
    val logDir = propertyManager.logDirUri
        .filterNotNull()
        .map { getLogDir() }

    private val logFiles = fileEventManager.onNewLogCreated
        .onStart { emit(Unit) }
        .flatMapLatest { logDir }
        .filterNotNull()
        .map { logDir -> logDir.listFiles() }

    val logFileNames: Flow<List<LogFileData>> = logFiles
        .map { files ->
            val extension = ".${AppProperties.LOG_FILE_EXTENSION}"
            files
                .filter {
                    it.isFile
                            && it.canWrite()
                            && it.name?.endsWith(extension) == true
                }
                .sortedBy { it.name }
                .map { LogFileData(it.name!!.removeSuffix(extension), it.uri) }
        }

    fun getLogDir(): DocumentFile? {
        val uri = propertyManager.getLogDirUri() ?: return null
        val documentFile = DocumentFile.fromTreeUri(context, Uri.parse(uri))
        return if (documentFile == null || !documentFile.isDirectory
            || !documentFile.canRead() || !documentFile.canWrite()
        ) null else documentFile
    }
}