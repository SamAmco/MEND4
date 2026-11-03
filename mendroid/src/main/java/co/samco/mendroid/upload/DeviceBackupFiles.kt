package co.samco.mendroid.upload

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import co.samco.mendroid.model.LocalLogDirectory
import co.samco.mendroid.model.PropertyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface ExistingBackupSource { suspend fun files(): List<LocalBackupFile> }

/** Enumerating device files is independent of Compose, queue transitions, and networking. */
class DeviceBackupFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    private val properties: PropertyManager,
    private val logs: LocalLogDirectory
) : ExistingBackupSource {
    override suspend fun files(): List<LocalBackupFile> = withContext(Dispatchers.IO) {
        val directory = properties.getEncDirUri()?.let { DocumentFile.fromTreeUri(context, it.toUri()) }
        if (directory == null || !directory.canRead()) throw UploadException(UploadFailure.LOCAL_FILE_UNAVAILABLE)
        val attachments = directory.listFiles().filter { it.isFile && it.name?.endsWith(".enc") == true }
            .map { LocalBackupFile(it.uri.toString(), requireNotNull(it.name), UploadKind.ATTACHMENT) }
        val logFiles = logs.files().map { LocalBackupFile(it.uri.toString(), it.name, UploadKind.LOG) }
        attachments + logFiles
    }
}
