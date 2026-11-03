package co.samco.mendroid.ui.screens

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import co.samco.mendroid.R
import co.samco.mendroid.nextcloud.NextcloudFailure
import co.samco.mendroid.upload.UploadFailure
import co.samco.mendroid.upload.asUploadFailure
import co.samco.mendroid.viewmodel.NextcloudUiError
import co.samco.mendroid.viewmodel.NextcloudUiProblem

@StringRes
internal fun uploadFailureMessage(failure: UploadFailure): Int = when (failure) {
    UploadFailure.LOCAL_WRITE_INTERRUPTED, UploadFailure.LOCAL_WRITE_FAILED -> R.string.upload_error_local_write
    UploadFailure.REGISTRATION_FAILED -> R.string.upload_error_unqueued
    UploadFailure.LOCAL_FILE_UNAVAILABLE -> R.string.upload_error_local_file
    UploadFailure.ACCOUNT_UNAVAILABLE -> R.string.upload_error_account
    UploadFailure.DESTINATION_COLLISION -> R.string.upload_error_collision
    UploadFailure.AUTHENTICATION -> R.string.upload_error_auth
    UploadFailure.ACCESS_DENIED -> R.string.upload_error_access
    UploadFailure.DESTINATION_MISSING -> R.string.upload_error_destination
    UploadFailure.REMOTE_CONFLICT -> R.string.upload_error_conflict
    UploadFailure.TOO_LARGE -> R.string.upload_error_too_large
    UploadFailure.STORAGE_FULL -> R.string.upload_error_storage
    UploadFailure.REDIRECT -> R.string.upload_error_redirect
    UploadFailure.SERVER_REJECTED, UploadFailure.INVALID_RESPONSE -> R.string.upload_error_response
    UploadFailure.CONNECTION, UploadFailure.SERVER_BUSY -> R.string.upload_error_connection
    UploadFailure.BACKGROUND_UNAVAILABLE -> R.string.upload_error_background
}

@Composable
internal fun NextcloudError(error: NextcloudUiError) {
    val message = when (error) {
        is NextcloudUiError.Backup -> uploadFailureMessage(error.reason)
        is NextcloudUiError.Nextcloud -> uploadFailureMessage(error.reason.asUploadFailure())
        is NextcloudUiError.Operation -> when (error.problem) {
            NextcloudUiProblem.INVALID_SETUP -> R.string.upload_error_setup
            NextcloudUiProblem.OPERATION_FAILED -> R.string.upload_error_operation
        }
    }
    Text(stringResource(message), color = MaterialTheme.colorScheme.error)
}

@StringRes
internal fun remoteReadFailureMessage(reason: NextcloudFailure): Int = when (reason) {
    NextcloudFailure.AUTHENTICATION, NextcloudFailure.ACCOUNT_UNAVAILABLE -> R.string.remote_read_auth
    NextcloudFailure.CONNECTION, NextcloudFailure.SERVER_BUSY -> R.string.remote_read_connection
    NextcloudFailure.DESTINATION_MISSING -> R.string.remote_read_missing
    NextcloudFailure.ACCESS_DENIED -> R.string.remote_read_access
    else -> R.string.remote_read_error
}
