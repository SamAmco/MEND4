package co.samco.mendroid.upload

import java.io.IOException

@JvmInline
value class AccountId(val value: String) {
    init { require(value.isNotBlank()) }
}

/** Decoded, account-relative DAV path. Encoding belongs exclusively to the HTTP client. */
@JvmInline
value class RemotePath private constructor(val value: String) {
    fun child(name: String): RemotePath {
        require(name.isNotBlank() && '/' !in name && name != "." && name != "..")
        return of("${value.trimEnd('/')}/$name")
    }
    val name: String get() = value.substringAfterLast('/')
    val parent: RemotePath get() = of(value.substringBeforeLast('/').ifEmpty { "/" })

    companion object {
        val ROOT = RemotePath("/")
        fun of(value: String): RemotePath {
            require(value.startsWith('/'))
            val segments = value.split('/').filter { it.isNotEmpty() }
            require(segments.none { it == "." || it == ".." })
            return RemotePath("/" + segments.joinToString("/"))
        }
    }
}

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
enum class BackupRegistration { NOT_CONFIGURED, QUEUED, NEEDS_ATTENTION }
data class LocalSaveResult(val backup: BackupRegistration)

class UploadException(val reason: UploadFailure, val statusCode: Int? = null) : IOException(reason.name)
