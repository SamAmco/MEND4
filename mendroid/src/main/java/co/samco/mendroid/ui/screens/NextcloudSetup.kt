package co.samco.mendroid.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import co.samco.mendroid.R
import co.samco.mendroid.upload.RemotePath
import co.samco.mendroid.upload.UploadConfig
import co.samco.mendroid.upload.UploadKind

@Composable
internal fun NextcloudConnectionForm(signingIn: Boolean, connect: (String) -> Unit, cancel: () -> Unit) {
    var server by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(value = server, onValueChange = { server = it }, singleLine = true,
        label = { Text(stringResource(R.string.nextcloud_server)) }, modifier = Modifier.fillMaxWidth())
    Text(stringResource(R.string.nextcloud_setup_help))
    Button(enabled = !signingIn && server.isNotBlank(), onClick = { connect(server) }) { Text(stringResource(R.string.connect_nextcloud)) }
    if (signingIn) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.nextcloud_finish_login))
        TextButton(onClick = cancel) { Text(stringResource(R.string.upload_cancel)) }
    }
}

@Composable
internal fun NextcloudDestinationSettings(
    config: UploadConfig, browse: (UploadKind, RemotePath) -> Unit, pause: (Boolean) -> Unit, mobileData: (Boolean) -> Unit
) {
    Text(config.account?.server.orEmpty())
    Text(config.account?.userId.orEmpty())
    TextButton(onClick = { browse(UploadKind.ATTACHMENT, config.attachments ?: RemotePath.ROOT) }) {
        Text(stringResource(R.string.remote_attachment_folder, config.attachments?.value ?: stringResource(R.string.upload_not_selected)))
    }
    TextButton(onClick = { browse(UploadKind.LOG, config.logs ?: RemotePath.ROOT) }) {
        Text(stringResource(R.string.remote_log_folder, config.logs?.value ?: stringResource(R.string.upload_not_selected)))
    }
    ToggleSettingRow(stringResource(R.string.pause_uploads), config.paused, pause)
    ToggleSettingRow(stringResource(R.string.upload_mobile_data), config.mobileData, mobileData)
    Text(stringResource(R.string.nextcloud_local_help))
    if (config.needsRescan) Text(stringResource(R.string.upload_error_unqueued))
}

@Composable
internal fun NextcloudBackupControls(importing: Boolean, retry: () -> Unit, disconnect: () -> Unit, existing: () -> Unit) {
    Row {
        TextButton(onClick = retry) { Text(stringResource(R.string.upload_retry)) }
        TextButton(onClick = disconnect) { Text(stringResource(R.string.nextcloud_disconnect)) }
    }
    TextButton(onClick = existing, enabled = !importing) { Text(stringResource(R.string.backup_existing_files)) }
    if (importing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
}
