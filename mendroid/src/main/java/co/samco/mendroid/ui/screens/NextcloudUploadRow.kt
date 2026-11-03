package co.samco.mendroid.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import co.samco.mendroid.R
import co.samco.mendroid.upload.AccountId
import co.samco.mendroid.upload.UploadEntry
import co.samco.mendroid.upload.UploadProgress
import co.samco.mendroid.upload.UploadState

@Composable
internal fun NextcloudUploadRow(
    entry: UploadEntry, progress: UploadProgress?, account: AccountId?, forget: (UploadEntry) -> Unit, verified: (UploadEntry) -> Unit
) {
    var checking by remember(entry.revision) { mutableStateOf(false) }
    Column {
        Text(entry.file.name, style = MaterialTheme.typography.titleSmall)
        Text(entry.target.path.value, style = MaterialTheme.typography.bodySmall)
        Text(stringResource(when (entry.state) {
            UploadState.WRITING -> R.string.upload_saving
            UploadState.PENDING -> R.string.uploads_waiting
            UploadState.UPLOADING -> R.string.upload_in_progress
            UploadState.UPLOADED -> R.string.upload_complete
            UploadState.ATTENTION -> R.string.upload_needs_attention
        }))
        if (progress?.revision == entry.revision) {
            LinearProgressIndicator(progress = { progress.percent / 100f }, modifier = Modifier.fillMaxWidth())
        }
        entry.failure?.let { Text(stringResource(uploadFailureMessage(it)), style = MaterialTheme.typography.bodySmall) }
        if (entry.failure?.requiresLocalVerification == true && entry.target.account == account) {
            TextButton(onClick = { checking = true }) { Text(stringResource(R.string.upload_verified_action)) }
        }
        if (entry.state == UploadState.ATTENTION || entry.state == UploadState.UPLOADED) {
            TextButton(onClick = { forget(entry) }) { Text(stringResource(R.string.upload_forget)) }
        }
        if (entry.target.account != account) Text(stringResource(R.string.upload_other_account), style = MaterialTheme.typography.bodySmall)
    }
    if (checking) AlertDialog(
        onDismissRequest = { checking = false },
        title = { Text(stringResource(R.string.upload_verified_action)) },
        text = { Text(stringResource(R.string.upload_verified_explanation)) },
        confirmButton = { TextButton(onClick = { checking = false; verified(entry) }) { Text(stringResource(R.string.upload_verified_confirm)) } },
        dismissButton = { TextButton(onClick = { checking = false }) { Text(stringResource(R.string.upload_cancel)) } }
    )
}
