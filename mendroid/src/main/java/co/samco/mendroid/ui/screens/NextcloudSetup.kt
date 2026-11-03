package co.samco.mendroid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.samco.mendroid.R
import co.samco.mendroid.nextcloud.NextcloudConfig
import co.samco.mendroid.nextcloud.RemotePath
import co.samco.mendroid.viewmodel.NextcloudFolderUse

@Composable
internal fun NextcloudConnectionForm(
    server: String, onServerChanged: (String) -> Unit, signingIn: Boolean,
    trustSelfSigned: Boolean, onTrustSelfSignedChanged: (Boolean) -> Unit
) =
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = server, onValueChange = onServerChanged, singleLine = true,
            enabled = !signingIn, label = { Text(stringResource(R.string.nextcloud_server)) },
            modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.nextcloud_setup_help))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = trustSelfSigned, onCheckedChange = onTrustSelfSignedChanged, enabled = !signingIn)
            Text(stringResource(R.string.nextcloud_trust_self_signed))
        }
        if (trustSelfSigned) Text(stringResource(R.string.nextcloud_trust_self_signed_help))
        if (signingIn) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.nextcloud_finish_login))
        }
    }

@Composable
internal fun NextcloudDestinationSettings(
    config: NextcloudConfig, browse: (NextcloudFolderUse, RemotePath) -> Unit, pause: (Boolean) -> Unit, mobileData: (Boolean) -> Unit
) {
    Text(config.account?.server.orEmpty())
    Text(config.account?.userId.orEmpty())
    TextButton(onClick = { browse(NextcloudFolderUse.ATTACHMENTS, config.attachments ?: RemotePath.ROOT) }) {
        Text(stringResource(R.string.remote_attachment_folder, config.attachments?.value ?: stringResource(R.string.upload_not_selected)))
    }
    TextButton(onClick = { browse(NextcloudFolderUse.LOGS, config.logs ?: RemotePath.ROOT) }) {
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

@Composable
internal fun AdditionalNextcloudFolders(config: NextcloudConfig, browse: () -> Unit, remove: (RemotePath) -> Unit) {
    Text(stringResource(R.string.additional_nextcloud_folders))
    Text(stringResource(R.string.additional_nextcloud_help))
    for (folder in config.additionalFolders) {
        Row(Modifier.fillMaxWidth()) {
            Text(folder.value, modifier = Modifier.weight(1f))
            TextButton(onClick = { remove(folder) }) { Text(stringResource(R.string.remove_read_folder)) }
        }
    }
    TextButton(onClick = browse) { Text(stringResource(R.string.add_read_folder)) }
}
