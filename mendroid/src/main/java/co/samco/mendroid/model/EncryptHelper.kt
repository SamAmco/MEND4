package co.samco.mendroid.model

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import co.samco.mend4.core.AppProperties
import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mendroid.R
import co.samco.mendroid.upload.BackupRegistration
import co.samco.mendroid.upload.LocalBackupFile
import co.samco.mendroid.upload.LocalBackupWriter
import co.samco.mendroid.upload.UploadKind
import co.samco.mendroid.upload.WriteMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext


interface EncryptHelper {
    val onFileEncrypted: Flow<String>

    suspend fun encryptFileFromUri(uri: Uri): Boolean
}

class EncryptHelperImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoProvider: CryptoProvider,
    private val errorToastManager: ErrorToastManager,
    private val propertyManager: PropertyManager,
    private val uploads: LocalBackupWriter
) : EncryptHelper {

    override val onFileEncrypted = MutableSharedFlow<String>()

    private val lastFileTime = AtomicLong()

    override suspend fun encryptFileFromUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val encDirUri = propertyManager.getEncDirUri()
        if (encDirUri == null) {
            errorToastManager.showErrorToast(R.string.no_enc_dir)
            return@withContext false
        }
        var newFile: DocumentFile? = null
        var completed = false
        try {
            val directory = DocumentFile.fromTreeUri(context, encDirUri.toUri())
            if (directory == null || !directory.isDirectory || !directory.canRead() || !directory.canWrite()) {
                errorToastManager.showErrorToast(R.string.no_enc_dir)
                return@withContext false
            }
            val time = lastFileTime.updateAndGet { maxOf(System.currentTimeMillis(), it + 1) }
            val fileName = SimpleDateFormat(AppProperties.ENC_FILE_NAME_FORMAT, Locale.ROOT).format(Date(time))
            val fullName = "$fileName.${AppProperties.ENC_FILE_EXTENSION}"
            // Never overwrite an existing attachment or produce a log reference to an auto-renamed file.
            require(directory.findFile(fullName) == null) { "Attachment name already exists" }
            val created = requireNotNull(directory.createFile("application/octet-stream", fullName))
            newFile = created
            require(created.name == fullName) { "Provider changed the attachment name" }
            val saved = uploads.write(LocalBackupFile(created.uri.toString(), fullName, UploadKind.ATTACHMENT), WriteMode.REPLACE) { output ->
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Input file unavailable" }
                    val extension = DocumentFile.fromSingleUri(context, uri)?.name
                        ?.substringAfterLast('.', "") ?: uri.lastPathSegment?.substringAfterLast('.', "").orEmpty()
                    cryptoProvider.encryptEncStream(input, output, extension)
                }
            }
            completed = true
            if (saved.backup == BackupRegistration.NEEDS_ATTENTION) errorToastManager.showErrorToast(R.string.encrypted_backup_pending)
            onFileEncrypted.emit(fileName)
            true
        } catch (e: CancellationException) {
            // Cancellation may arrive after the local stream closed. Preserve the encrypted file;
            // an interrupted write remains protected from automatic retry in the queue.
            throw e
        } catch (_: Exception) {
            if (!completed) newFile?.delete()
            errorToastManager.showErrorToast(R.string.failed_to_create_file)
            false
        }
    }
}
