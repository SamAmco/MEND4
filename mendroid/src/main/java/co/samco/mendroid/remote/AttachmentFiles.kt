package co.samco.mendroid.remote

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import co.samco.mendroid.model.PropertyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The user's enc directory takes precedence; remote sources are only read when it has no usable match. */
class AttachmentFiles internal constructor(
    private val local: (String) -> Uri?,
    private val remote: NextcloudReadFiles
) {
    @Inject constructor(@ApplicationContext context: Context, properties: PropertyManager, remote: NextcloudReadFiles) : this({ name ->
        try {
            properties.getEncDirUri()?.toUri()?.let { DocumentFile.fromTreeUri(context, it) }
                ?.findFile(name)?.takeIf { it.isFile && it.canRead() }?.uri
        } catch (_: SecurityException) { null }
        catch (_: IllegalArgumentException) { null }
    }, remote)

    suspend fun find(name: String): Uri? = withContext(Dispatchers.IO) {
        require(name.endsWith(".enc") && '/' !in name && '\\' !in name)
        local(name) ?: remote.findAttachment(name)?.toUri()
    }
}
