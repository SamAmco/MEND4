package co.samco.mendroid.model

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Direct children of the configured on-device directory are the complete log collection. */
class LocalLogDirectory internal constructor(private val directory: () -> DocumentFile?) {
    @Inject constructor(@ApplicationContext context: Context, properties: PropertyManager) : this({
        properties.getLogDirUri()?.toUri()?.takeIf(::isLocalTree)
            ?.let { DocumentFile.fromTreeUri(context, it) }
    })

    suspend fun files(): List<LogFileData> = withContext(Dispatchers.IO) {
        val folder = readableDirectory()
        folder.listFiles().filter { it.isFile && it.name?.endsWith(EXTENSION) == true }
            .map { LogFileData(requireNotNull(it.name), it.uri) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    suspend fun create(name: String): LogFileData = withContext(Dispatchers.IO) {
        val filename = filename(name)
        val folder = readableDirectory()
        if (!folder.canWrite()) throw IOException("Log directory is not writable")
        if (folder.findFile(filename) != null) throw FileAlreadyExistsException(filename)
        val file = folder.createFile("application/x-mend", filename)
            ?: throw IOException("Cannot create log")
        if (file.name != filename) {
            file.delete()
            throw IOException("Provider changed the requested log name")
        }
        LogFileData(filename, file.uri)
    }

    private fun readableDirectory(): DocumentFile = directory()?.takeIf { it.isDirectory && it.canRead() }
        ?: throw IOException("Log directory is unavailable")

    companion object {
        private const val EXTENSION = ".mend"

        // Restrict to Android's on-device storage provider, including removable storage.
        fun isLocalTree(uri: Uri): Boolean = uri.scheme == "content" &&
            uri.authority == "com.android.externalstorage.documents" && DocumentsContract.isTreeUri(uri)

        private fun filename(name: String): String {
            val stem = name.trim().removeSuffix(EXTENSION)
            require(stem.isNotBlank() && stem != "." && stem != ".." &&
                stem.none { it == '/' || it == '\\' || it.isISOControl() })
            return "$stem$EXTENSION"
        }
    }
}
