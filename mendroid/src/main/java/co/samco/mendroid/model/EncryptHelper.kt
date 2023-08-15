package co.samco.mendroid.model

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import co.samco.mend4.core.AppProperties
import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mendroid.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject


interface EncryptHelper {
    val onFileEncrypted: Flow<String>

    suspend fun encryptFileFromUri(uri: Uri)
}

class EncryptHelperImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoProvider: CryptoProvider,
    private val errorToastManager: ErrorToastManager,
    private val propertyManager: PropertyManager
) : EncryptHelper {

    override val onFileEncrypted = MutableSharedFlow<String>()

    override suspend fun encryptFileFromUri(uri: Uri) {
        val inputStream = context.contentResolver.openInputStream(uri)
        if (inputStream == null) {
            errorToastManager.showErrorToast(R.string.no_file)
            return
        }

        val encDirUri = propertyManager.getEncDirUri()
        if (encDirUri == null) {
            errorToastManager.showErrorToast(R.string.no_enc_dir)
            return
        }

        val encDirFile = DocumentFile.fromTreeUri(context, Uri.parse(encDirUri))
        if (encDirFile == null || !encDirFile.isDirectory
            || !encDirFile.canRead() || !encDirFile.canWrite()
        ) {
            errorToastManager.showErrorToast(R.string.no_enc_dir)
            return
        }

        val fileName = SimpleDateFormat(
            AppProperties.ENC_FILE_NAME_FORMAT,
            Locale.getDefault()
        ).format(Date())

        val newFile = encDirFile.createFile(
            "application/octet-stream",
            "$fileName.${AppProperties.ENC_FILE_EXTENSION}"
        )
        if (newFile == null || !newFile.isFile || !newFile.canWrite()) {
            errorToastManager.showErrorToast(R.string.failed_to_create_file)
            return
        }

        val outputStream = context.contentResolver.openOutputStream(newFile.uri)
        if (outputStream == null) {
            errorToastManager.showErrorToast(R.string.failed_to_create_file)
            return
        }

        val fileExtension = uri.lastPathSegment
            ?.substringAfterLast('.', "")
            ?: ""

        cryptoProvider.encryptEncStream(inputStream, outputStream, fileExtension)

        onFileEncrypted.emit(fileName)
    }

}