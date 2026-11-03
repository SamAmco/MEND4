package co.samco.mendroid.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import co.samco.mendroid.R
import co.samco.mendroid.ui.common.ConfirmCancelDialogBody
import co.samco.mendroid.ui.common.TextItemList
import co.samco.mendroid.ui.theme.mendTextButtonColors
import co.samco.mendroid.ui.theme.mendTextFieldColors
import co.samco.mendroid.viewmodel.AudioRecordingViewModel
import co.samco.mendroid.viewmodel.AudioRecordingViewModelImpl
import co.samco.mendroid.viewmodel.EncryptViewModel

@Composable
fun EncryptScreen(modifier: Modifier = Modifier) = Box(modifier) {

    val viewModel = viewModel<EncryptViewModel>()
    val audioRecordingViewModel: AudioRecordingViewModel = viewModel<AudioRecordingViewModelImpl>()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME, onEvent = viewModel::refreshLogs)

    EncryptScreenMain()

    if (viewModel.loading) LoadingOverlay()

    if (viewModel.showDeleteFileDialog.collectAsState().value) {
        OfferDeleteFileDialog()
    }

    if (audioRecordingViewModel.showAudioRecordingDialog.collectAsState().value) {
        RecordAudioDialog()
    }
}

@Composable
private fun LoadingOverlay() = Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
) {
    CircularProgressIndicator()
}

@Composable
private fun EncryptScreenMain() = Column(modifier = Modifier.fillMaxSize()) {
    val viewModel = viewModel<EncryptViewModel>()

    if (viewModel.showAttachmentMenu) AttachmentMenu { viewModel.hideAttachmentMenu() }

    Spacer(modifier = Modifier.size(8.dp))

    val focusRequester = remember { FocusRequester() }

    Card(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        shape = MaterialTheme.shapes.small,
    ) {
        TextField(
            modifier = Modifier.fillMaxSize().focusRequester(focusRequester),
            enabled = !viewModel.loading,
            value = viewModel.currentEntryText,
            onValueChange = { viewModel.currentEntryText = it },
            keyboardOptions = KeyboardOptions.Default.copy(
                capitalization = KeyboardCapitalization.Sentences
            ),
            colors = mendTextFieldColors()
        )
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Spacer(modifier = Modifier.size(8.dp))

    Row(
        modifier = Modifier
            .wrapContentHeight()
            .padding(vertical = 4.dp),
    ) {

        SelectLogButton(
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
        )

        Spacer(modifier = Modifier.size(8.dp))

        Button(
            modifier = Modifier.height(42.dp),
            onClick = { viewModel.showAttachmentMenu() },
            enabled = !viewModel.loading
        ) {
            Icon(
                imageVector = Icons.Filled.Attachment,
                contentDescription = stringResource(id = R.string.attachment)
            )
        }

        Spacer(modifier = Modifier.size(8.dp))

        ActionButton(
            modifier = Modifier.height(42.dp),
            text = stringResource(id = R.string.submit),
            enabled = viewModel.submitButtonEnabled.collectAsState().value
        ) {
            viewModel.encryptText()
        }
    }
}

private fun checkHasMicrophonePermission(context: Context): Boolean {
    return context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PERMISSION_GRANTED
}

@Composable
private fun AttachmentMenu(onDismiss: () -> Unit) = Dialog(onDismissRequest = onDismiss) {
    val viewModel = viewModel<EncryptViewModel>()

    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            val photoLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.TakePicture(),
                viewModel::onPhotoTaken
            )

            MenuButton(R.string.take_photo) {
                viewModel.preparePhotoUri()?.let { photoLauncher.launch(it) }
            }

            HorizontalDivider()

            VideoButton()

            HorizontalDivider()

            val audioRecordingViewModel: AudioRecordingViewModel =
                viewModel<AudioRecordingViewModelImpl>()

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) {
                if (it) {
                    audioRecordingViewModel.showAudioRecordingDialog()
                    viewModel.hideAttachmentMenu()
                }
            }

            MenuButton(R.string.record_audio)
            {
                if (checkHasMicrophonePermission(context)) {
                    audioRecordingViewModel.showAudioRecordingDialog()
                    viewModel.hideAttachmentMenu()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }

            HorizontalDivider()

            val fileLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
                viewModel::encryptFile
            )

            MenuButton(R.string.select_file)
            {
                fileLauncher.launch(arrayOf("*/*"))
            }
        }
    }
}

@Composable
private fun VideoButton() {
    val viewModel = viewModel<EncryptViewModel>()

    val videoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo(),
        viewModel::onVideoTaken
    )

    // CaptureVideo grants access to our own output URI; no media library access is needed.
    MenuButton(R.string.record_video) {
        viewModel.prepareVideoUri()?.let { videoLauncher.launch(it) }
    }
}

@Composable
private fun MenuButton(
    stringId: Int,
    onClick: () -> Unit
) = TextButton(
    modifier = Modifier.fillMaxWidth(),
    colors = mendTextButtonColors(),
    onClick = onClick
) {
    Text(
        text = stringResource(id = stringId),
        style = MaterialTheme.typography.labelLarge
    )
}

@Composable
private fun OfferDeleteFileDialog() {
    val viewModel = viewModel<EncryptViewModel>()
    Dialog(
        onDismissRequest = { viewModel.dismissOfferDeleteFileDialog() },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        ConfirmCancelDialogBody(
            text = stringResource(id = R.string.delete_file_question),
            cancelText = stringResource(id = R.string.no),
            confirmText = stringResource(id = R.string.yes),
            onCancelClicked = viewModel::dismissOfferDeleteFileDialog,
            onConfirmClicked = viewModel::deleteFile
        )
    }
}

@Composable
private fun SelectLogButton(modifier: Modifier) {
    val viewModel = viewModel<EncryptViewModel>()

    val logName by viewModel.currentLogName.collectAsState()

    Button(
        modifier = modifier,
        onClick = viewModel::onSelectLogButtonClicked,
        enabled = !viewModel.loading,
        colors = if (logName == null) ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error
        ) else ButtonDefaults.buttonColors(),
    ) {
        Text(
            text = logName ?: stringResource(id = R.string.select_log),
            style = MaterialTheme.typography.labelLarge
        )
    }

    if (viewModel.showSelectLogDialog.collectAsState().value) SelectLogDialog()
}

@Composable
private fun SelectLogDialog() {
    val viewModel = viewModel<EncryptViewModel>()

    if (viewModel.showCreateLogDialog) {
        CreateLogDialog(viewModel)
        return
    }

    Dialog(onDismissRequest = viewModel::hideSelectLogDialog) {
        Surface(shape = MaterialTheme.shapes.small) {
            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                val logs by viewModel.logs.collectAsState()
                TextItemList(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight().heightIn(min = 160.dp, max = 320.dp),
                    items = logs,
                    onItemClicked = viewModel::onLogSelected,
                    itemText = { it.name }
                )
                ActionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.create),
                    onClick = viewModel::showCreateLog
                )
            }
        }
    }
}

@Composable
private fun CreateLogDialog(viewModel: EncryptViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::hideCreateLog,
        title = { Text(stringResource(R.string.create_log)) },
        text = {
            Column {
                TextField(
                    value = viewModel.newLogName,
                    onValueChange = { viewModel.newLogName = it },
                    label = { Text(stringResource(R.string.log_name)) },
                    singleLine = true,
                    enabled = !viewModel.loading,
                    isError = viewModel.logCreationError != null
                )
                viewModel.logCreationError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::createLog, enabled = !viewModel.loading) { Text(stringResource(R.string.create)) }
        },
        dismissButton = {
            TextButton(onClick = viewModel::hideCreateLog, enabled = !viewModel.loading) { Text(stringResource(R.string.cancel)) }
        }
    )
}


@Composable
private fun ActionButton(
    modifier: Modifier = Modifier,
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) = Button(
    modifier = modifier,
    onClick = onClick,
    enabled = enabled
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge
    )
}
