package co.samco.mendroid.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import co.samco.mendroid.R
import co.samco.mendroid.upload.UploadState
import co.samco.mendroid.viewmodel.NextcloudFolderUse
import co.samco.mendroid.viewmodel.NextcloudEffect
import co.samco.mendroid.viewmodel.NextcloudUiState
import co.samco.mendroid.viewmodel.NextcloudViewModel

@Composable
fun NextcloudUploadsButton() {
    val model = viewModel<NextcloudViewModel>()
    val state by model.state.collectAsState()
    val context = LocalContext.current
    var showing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(model) {
        model.effects.collect { effect ->
            when (effect) {
                is NextcloudEffect.OpenBrowser -> try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, effect.url.toUri()))
                } catch (_: Exception) { model.browserFailed() }
            }
        }
    }
    TextButton(onClick = { showing = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(summary(state))) }
    if (showing) {
        val picker = state.actions.picker
        if (picker != null) NextcloudFolderPicker(picker, model::browse, model::selectFolder, model::closeFolderPicker)
        else UploadsDialog(state, model) { showing = false }
    }
}

private fun summary(state: NextcloudUiState): Int = when {
    state.config.credentialError -> R.string.upload_credentials_unavailable
    state.config.account == null -> R.string.connect_nextcloud
    state.config.attachments == null || state.config.logs == null ->
        if (state.config.additionalFolders.isEmpty()) R.string.choose_backup_folders else R.string.nextcloud_files_and_uploads
    state.config.needsRescan -> R.string.upload_needs_attention
    state.config.paused -> R.string.uploads_paused
    state.entries.any { it.state == UploadState.ATTENTION } -> R.string.upload_needs_attention
    state.entries.any { it.state == UploadState.UPLOADING } -> R.string.upload_in_progress
    state.entries.any { it.state == UploadState.PENDING || it.state == UploadState.WRITING } -> R.string.uploads_waiting
    state.entries.isEmpty() -> R.string.no_uploads_yet
    else -> R.string.all_files_uploaded
}

@Composable
private fun UploadsDialog(state: NextcloudUiState, model: NextcloudViewModel, close: () -> Unit) {
    var server by rememberSaveable { mutableStateOf("") }
    val connecting = state.config.account == null
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(if (connecting) R.string.connect_nextcloud else R.string.nextcloud_files_and_uploads)) },
        confirmButton = {
            when {
                !connecting -> TextButton(onClick = close) { Text(stringResource(R.string.upload_close)) }
                state.actions.signingIn -> TextButton(onClick = model::cancelLogin) { Text(stringResource(R.string.upload_cancel)) }
                else -> TextButton(enabled = server.isNotBlank(), onClick = { model.connect(server) }) {
                    Text(stringResource(R.string.connect_nextcloud))
                }
            }
        },
        dismissButton = {
            if (connecting) TextButton(onClick = close) { Text(stringResource(R.string.upload_close)) }
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.heightIn(max = 560.dp)) {
                item {
                    if (state.config.account == null) {
                        NextcloudConnectionForm(server, { server = it }, state.actions.signingIn)
                    } else {
                        NextcloudDestinationSettings(state.config, model::browse, model::pause, model::mobileData)
                        AdditionalNextcloudFolders(state.config,
                            { model.browse(NextcloudFolderUse.ADDITIONAL) }, model::removeAdditionalFolder)
                        NextcloudBackupControls(state.actions.importing, model::retry, model::disconnect, model::backupExisting)
                    }
                }
                state.actions.error?.let { error -> item { NextcloudError(error) } }
                if (state.actions.imported) item { Text(stringResource(R.string.upload_existing_queued)) }
                if (state.entries.isNotEmpty()) item { HorizontalDivider() }
                items(state.entries, key = { it.revision.id }) { entry ->
                    NextcloudUploadRow(entry, state.progress, state.config.account?.id, model::forget, model::backupVerified)
                }
            }
        }
    )
}
