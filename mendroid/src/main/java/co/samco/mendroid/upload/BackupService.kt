package co.samco.mendroid.upload

import co.samco.mendroid.nextcloud.CredentialRead
import co.samco.mendroid.nextcloud.CredentialStore
import co.samco.mendroid.nextcloud.NextcloudSession
import co.samco.mendroid.nextcloud.NextcloudSettings
import co.samco.mendroid.nextcloud.RemoteFolder
import co.samco.mendroid.nextcloud.RemoteFolderBrowser
import co.samco.mendroid.nextcloud.RemotePath
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Application commands exposed to presentation; persistence and credentials stay encapsulated. */
class BackupService @Inject constructor(
    private val settings: NextcloudSettings,
    private val credentials: CredentialStore,
    private val queue: UploadQueue,
    private val writer: LocalBackupWriter,
    private val existing: ExistingBackupSource,
    private val folders: RemoteFolderBrowser,
    private val wakeup: UploadWakeup,
    runner: UploadRunner
) {
    val config = settings.config
    val entries = queue.entries
    val progress = runner.progress

    suspend fun refresh() { queue.refreshEntries() }
    suspend fun connect(session: NextcloudSession) = withContext(Dispatchers.IO) {
        settings.connect(session)
        retry()
    }
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        settings.disconnect()
        wakeup.rescheduleUploads()
    }
    private fun session(): NextcloudSession = (credentials.read() as? CredentialRead.Available)?.session
        ?: throw UploadException(UploadFailure.ACCOUNT_UNAVAILABLE)
    suspend fun folders(path: RemotePath): List<RemoteFolder> = folders.folders(session(), path)
    suspend fun selectFolder(kind: UploadKind, path: RemotePath) = withContext(Dispatchers.IO) {
        when (kind) {
            UploadKind.ATTACHMENT -> settings.selectAttachmentFolder(path)
            UploadKind.LOG -> settings.selectLogFolder(path)
        }
        wakeup.requestUpload()
    }
    suspend fun addAdditionalFolder(path: RemotePath) = withContext(Dispatchers.IO) { settings.addAdditionalFolder(path) }
    suspend fun removeAdditionalFolder(path: RemotePath) = withContext(Dispatchers.IO) { settings.removeAdditionalFolder(path) }
    suspend fun pause(value: Boolean) = withContext(Dispatchers.IO) {
        settings.pause(value)
        wakeup.rescheduleUploads()
    }
    suspend fun allowMobileData(value: Boolean) = withContext(Dispatchers.IO) {
        settings.allowMobileData(value)
        wakeup.rescheduleUploads()
    }
    suspend fun retry() { queue.retryFailures(); wakeup.rescheduleUploads() }
    suspend fun forget(entry: UploadEntry) { queue.forget(entry.revision) }

    suspend fun backupExisting() {
        val destination = config.value
        require(destination.account != null && destination.attachments != null && destination.logs != null)
        queue.recoverAfterRestart()
        for (file in existing.files()) {
            val registered = writer.enqueueExisting(file, destination.target(file))
            check(registered == BackupRegistration.QUEUED || registered == BackupRegistration.SKIPPED)
        }
        settings.acknowledgeRescan()
    }

    suspend fun backupVerified(entry: UploadEntry) {
        require(entry.failure?.requiresLocalVerification == true)
        require(config.value.account?.id == entry.target.account)
        check(writer.enqueueExisting(entry.file, entry.target, verifiedRevision = entry.revision) == BackupRegistration.QUEUED)
    }
}
