package co.samco.mendroid.nextcloud

import java.io.IOException

/** Connection and server failures shared by login, browsing, downloads, and uploads. */
enum class NextcloudFailure {
    CERTIFICATE, ACCOUNT_UNAVAILABLE, AUTHENTICATION, ACCESS_DENIED, DESTINATION_MISSING, REMOTE_CONFLICT,
    TOO_LARGE, STORAGE_FULL, REDIRECT, SERVER_REJECTED, INVALID_RESPONSE, CONNECTION, SERVER_BUSY
}

class NextcloudException(val reason: NextcloudFailure, val statusCode: Int? = null) : IOException(reason.name)
