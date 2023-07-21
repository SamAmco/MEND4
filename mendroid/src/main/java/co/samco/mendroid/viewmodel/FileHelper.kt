@file:OptIn(ExperimentalCoroutinesApi::class)

package co.samco.mendroid.viewmodel

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import co.samco.mend4.core.AppProperties
import co.samco.mendroid.model.PropertyManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class FileHelper(
    private val context: Context,
    private val propertyManager: PropertyManager
) {
    val onNewLogCreated = MutableSharedFlow<Unit>()

    val logDir = propertyManager.logDirUri
        .filterNotNull()
        .map { getLogDir() }

    val logFileNames = onNewLogCreated
        .onStart { emit(Unit) }
        .flatMapLatest { logDir }
        .filterNotNull()
        .map { logDir ->
            val extension = ".${AppProperties.LOG_FILE_EXTENSION}"
            logDir.listFiles()
                .filter {
                    it.isFile
                            && it.canWrite()
                            && it.name?.endsWith(extension) == true
                }
                .sortedBy { it.name }
                .map { it.name!!.removeSuffix(extension) }
        }

    fun getLogDir(): DocumentFile? {
        val uri = propertyManager.getLogDirUri() ?: return null
        val documentFile = DocumentFile.fromTreeUri(context, Uri.parse(uri))
        return if (documentFile == null || !documentFile.isDirectory
            || !documentFile.canRead() || !documentFile.canWrite()
        ) null else documentFile
    }
}