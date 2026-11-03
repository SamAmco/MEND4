package co.samco.mendroid.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.samco.mendroid.R
import co.samco.mendroid.nextcloud.RemotePath
import co.samco.mendroid.viewmodel.FolderPickerState
import co.samco.mendroid.viewmodel.NextcloudFolderUse

@Composable
internal fun NextcloudFolderPicker(
    picker: FolderPickerState, browse: (NextcloudFolderUse, RemotePath) -> Unit, select: () -> Unit, close: () -> Unit
) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(when (picker.use) {
            NextcloudFolderUse.ATTACHMENTS -> R.string.choose_attachment_folder
            NextcloudFolderUse.LOGS -> R.string.choose_log_folder
            NextcloudFolderUse.ADDITIONAL -> R.string.add_read_folder
        })) },
        confirmButton = {
            TextButton(onClick = select, enabled = !picker.loading && picker.error == null) { Text(stringResource(R.string.use_this_folder)) }
        },
        dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.upload_cancel)) } },
        text = {
            Column {
                Text(picker.path.value)
                if (picker.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (picker.path != RemotePath.ROOT) {
                    TextButton(onClick = { browse(picker.use, picker.path.parent) }) { Text(stringResource(R.string.parent_folder)) }
                }
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(picker.folders, key = { it.path.value }) { child ->
                        TextButton(onClick = { browse(picker.use, child.path) }) { Text(child.name) }
                    }
                }
                picker.error?.let { NextcloudError(it) }
            }
        }
    )
}
