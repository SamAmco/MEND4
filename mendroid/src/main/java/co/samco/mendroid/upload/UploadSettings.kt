package co.samco.mendroid.upload

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UploadConfig(
    val account: NextcloudAccount? = null,
    val attachments: RemotePath? = null,
    val logs: RemotePath? = null,
    val paused: Boolean = false,
    val mobileData: Boolean = false,
    val credentialError: Boolean = false,
    val needsRescan: Boolean = false,
    val additionalFolders: List<RemotePath> = emptyList()
) {
    fun target(file: LocalBackupFile): UploadTarget? {
        val identity = account ?: return null
        val folder = when (file.kind) {
            UploadKind.ATTACHMENT -> attachments
            UploadKind.LOG -> logs
        } ?: return null
        return UploadTarget(identity.id, folder.child(file.name))
    }
}

@Singleton
class UploadSettings @Inject constructor(
    @ApplicationContext context: Context,
    private val credentials: CredentialStore
) {
    private val prefs = context.getSharedPreferences("nextcloud", Context.MODE_PRIVATE)
    private val mutableConfig = MutableStateFlow(load())
    val config = mutableConfig.asStateFlow()

    private fun load(): UploadConfig {
        val credential = credentials.read()
        val account = (credential as? CredentialRead.Available)?.session?.account
        val ownsFolders = account != null && prefs.getString("folderAccount", null) == account.id.value
        fun folder(key: String): RemotePath? = if (!ownsFolders) null else prefs.getString(key, null)?.let(RemotePath::of)
        val additional = if (ownsFolders) prefs.getStringSet("additionalFolders", emptySet()).orEmpty()
            .map(RemotePath::of).sortedBy { it.value } else emptyList()
        return UploadConfig(account, folder("attachments"), folder("logs"), prefs.getBoolean("paused", false),
            prefs.getBoolean("mobileData", false), credential == CredentialRead.Unavailable, prefs.getBoolean("needsRescan", false), additional)
    }

    @Synchronized
    fun connect(session: NextcloudSession) {
        credentials.save(session)
        if (prefs.getString("folderAccount", null) != session.account.id.value) prefs.edit {
            remove("attachments"); remove("logs"); remove("additionalFolders"); putString("folderAccount", session.account.id.value)
        }
        mutableConfig.value = load()
    }

    @Synchronized
    fun selectFolder(kind: UploadKind, path: RemotePath) {
        check(mutableConfig.value.account != null)
        prefs.edit { putString(if (kind == UploadKind.ATTACHMENT) "attachments" else "logs", path.value) }
        mutableConfig.value = load()
    }

    @Synchronized
    fun addAdditionalFolder(path: RemotePath) {
        check(mutableConfig.value.account != null)
        val folders = mutableConfig.value.additionalFolders + path
        prefs.edit { putStringSet("additionalFolders", folders.map { it.value }.toSet()) }
        mutableConfig.value = load()
    }

    @Synchronized
    fun removeAdditionalFolder(path: RemotePath) {
        prefs.edit { putStringSet("additionalFolders", mutableConfig.value.additionalFolders.filterNot { it == path }.map { it.value }.toSet()) }
        mutableConfig.value = load()
    }

    @Synchronized
    fun pause(value: Boolean) {
        prefs.edit { putBoolean("paused", value) }
        mutableConfig.value = mutableConfig.value.copy(paused = value)
    }

    @Synchronized
    fun allowMobileData(value: Boolean) {
        prefs.edit { putBoolean("mobileData", value) }
        mutableConfig.value = mutableConfig.value.copy(mobileData = value)
    }

    @Synchronized
    fun recordUnqueuedSave() {
        prefs.edit { putBoolean("needsRescan", true) }
        mutableConfig.value = mutableConfig.value.copy(needsRescan = true)
    }

    @Synchronized
    fun acknowledgeRescan() {
        prefs.edit { putBoolean("needsRescan", false) }
        mutableConfig.value = mutableConfig.value.copy(needsRescan = false)
    }

    @Synchronized
    fun disconnect() { credentials.clear(); mutableConfig.value = load() }
}
