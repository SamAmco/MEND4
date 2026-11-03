package co.samco.mendroid.upload

import co.samco.mendroid.nextcloud.AccountId
import co.samco.mendroid.nextcloud.NextcloudConfig
import co.samco.mendroid.nextcloud.NextcloudFailure
import co.samco.mendroid.nextcloud.RemotePath
import java.io.IOException

enum class OverwritePolicy { REPLACE, REQUIRE_IDENTICAL }
enum class UploadKind(val overwrite: OverwritePolicy) {
    ATTACHMENT(OverwritePolicy.REQUIRE_IDENTICAL), LOG(OverwritePolicy.REPLACE)
}
data class LocalBackupFile(val uri: String, val name: String, val kind: UploadKind)
data class UploadTarget(val account: AccountId, val path: RemotePath)
data class UploadRevision(val id: Long, val number: Long)
class WriteToken internal constructor(val revision: UploadRevision)
class UploadToken internal constructor(val revision: UploadRevision)
enum class Transition { APPLIED, SUPERSEDED }
enum class UploadState { WRITING, PENDING, UPLOADING, UPLOADED, ATTENTION }

/** Persist reasons, not presentation text. Local write failures require explicit verification. */
enum class UploadFailure(val automaticRetry: Boolean = false, val requiresLocalVerification: Boolean = false) {
    LOCAL_WRITE_INTERRUPTED(requiresLocalVerification = true),
    LOCAL_WRITE_FAILED(requiresLocalVerification = true),
    REGISTRATION_FAILED, LOCAL_FILE_UNAVAILABLE, ACCOUNT_UNAVAILABLE, DESTINATION_COLLISION,
    AUTHENTICATION, ACCESS_DENIED, DESTINATION_MISSING, REMOTE_CONFLICT,
    TOO_LARGE, STORAGE_FULL, REDIRECT, SERVER_REJECTED, INVALID_RESPONSE,
    CONNECTION(automaticRetry = true), SERVER_BUSY(automaticRetry = true), BACKGROUND_UNAVAILABLE
}
data class UploadEntry(
    val revision: UploadRevision,
    val file: LocalBackupFile,
    val target: UploadTarget,
    val state: UploadState,
    val failure: UploadFailure? = null
)
data class UploadProgress(val revision: UploadRevision, val percent: Int)
enum class RunResult { COMPLETE, WAITING, RETRY }
enum class BackupRegistration { NOT_CONFIGURED, QUEUED, NEEDS_ATTENTION, SKIPPED }
data class LocalSaveResult(val backup: BackupRegistration)

class UploadException(val reason: UploadFailure) : IOException(reason.name)

/** Upload-specific interpretation of the shared connection and folder settings. */
fun NextcloudConfig.target(file: LocalBackupFile): UploadTarget? {
    val identity = account ?: return null
    val folder = when (file.kind) {
        UploadKind.ATTACHMENT -> attachments
        UploadKind.LOG -> logs
    } ?: return null
    return UploadTarget(identity.id, folder.child(file.name))
}

fun NextcloudFailure.asUploadFailure(): UploadFailure = when (this) {
    NextcloudFailure.ACCOUNT_UNAVAILABLE -> UploadFailure.ACCOUNT_UNAVAILABLE
    NextcloudFailure.AUTHENTICATION -> UploadFailure.AUTHENTICATION
    NextcloudFailure.ACCESS_DENIED -> UploadFailure.ACCESS_DENIED
    NextcloudFailure.DESTINATION_MISSING -> UploadFailure.DESTINATION_MISSING
    NextcloudFailure.REMOTE_CONFLICT -> UploadFailure.REMOTE_CONFLICT
    NextcloudFailure.TOO_LARGE -> UploadFailure.TOO_LARGE
    NextcloudFailure.STORAGE_FULL -> UploadFailure.STORAGE_FULL
    NextcloudFailure.REDIRECT -> UploadFailure.REDIRECT
    NextcloudFailure.SERVER_REJECTED -> UploadFailure.SERVER_REJECTED
    NextcloudFailure.INVALID_RESPONSE -> UploadFailure.INVALID_RESPONSE
    NextcloudFailure.CONNECTION -> UploadFailure.CONNECTION
    NextcloudFailure.SERVER_BUSY -> UploadFailure.SERVER_BUSY
}

/** Protection belongs to the source file, including queue entries for other remote destinations. */
val UploadEntry.protectsLocalFile: Boolean
    get() = state == UploadState.WRITING || failure?.requiresLocalVerification == true
