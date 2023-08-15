package co.samco.mendroid.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.viewmodel.compose.viewModel
import co.samco.mend4.core.AppProperties
import co.samco.mendroid.R
import co.samco.mendroid.ui.common.ConfirmCancelDialogBody
import co.samco.mendroid.ui.common.TextItemList
import co.samco.mendroid.ui.theme.mendTextButtonColors
import co.samco.mendroid.ui.theme.mendTextFieldColors
import co.samco.mendroid.viewmodel.AudioRecordingViewModel
import co.samco.mendroid.viewmodel.EncryptViewModel

@Composable
fun EncryptScreen(
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester
) = Box(modifier) {

    val viewModel = viewModel<EncryptViewModel>()
    val audioRecordingViewModel = viewModel<AudioRecordingViewModel>()

    EncryptScreenMain(focusRequester = focusRequester)

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
private fun EncryptScreenMain(
    focusRequester: FocusRequester
) = Column(modifier = Modifier.fillMaxSize()) {
    val viewModel = viewModel<EncryptViewModel>()

    if (viewModel.showAttachmentMenu) AttachmentMenu { viewModel.hideAttachmentMenu() }

    Spacer(modifier = Modifier.size(8.dp))

    Card(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .border(
                border = BorderStroke(1.dp, MaterialTheme.colors.primary),
                shape = MaterialTheme.shapes.small
            ),
    ) {
        TextField(
            modifier = Modifier.focusRequester(focusRequester),
            enabled = !viewModel.loading,
            value = viewModel.currentEntryText,
            onValueChange = { viewModel.currentEntryText = it },
            keyboardOptions = KeyboardOptions.Default.copy(
                capitalization = KeyboardCapitalization.Sentences
            ),
            colors = mendTextFieldColors()
        )
    }

    Spacer(modifier = Modifier.size(8.dp))

    Row(
        modifier = Modifier
            .wrapContentHeight()
            .padding(vertical = 4.dp),
    ) {

        SelectLogButton(modifier = Modifier.weight(1f))

        Spacer(modifier = Modifier.size(8.dp))

        Button(
            onClick = { viewModel.showAttachmentMenu() },
            enabled = !viewModel.loading
        ) {
            Icon(
                imageVector = Icons.Filled.Attachment,
                contentDescription = stringResource(id = R.string.attachment)
            )
        }

        Spacer(modifier = Modifier.size(8.dp))

        val currentLog = viewModel.currentLogName.collectAsState().value

        ActionButton(
            text = stringResource(id = R.string.submit),
            enabled = currentLog != null && !viewModel.loading,
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

            Divider()

            val videoLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CaptureVideo(),
                viewModel::onVideoTaken
            )

            MenuButton(R.string.record_video) {
                viewModel.prepareVideoUri()?.let { videoLauncher.launch(it) }
            }

            Divider()

            val audioRecorderViewModel = viewModel<AudioRecordingViewModel>()

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) {
                if (it) {
                    audioRecorderViewModel.showAudioRecordingDialog()
                    viewModel.hideAttachmentMenu()
                }
            }

            MenuButton(R.string.record_audio) {
                if (checkHasMicrophonePermission(context)) {
                    audioRecorderViewModel.showAudioRecordingDialog()
                    viewModel.hideAttachmentMenu()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }

            Divider()

            val fileLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
                viewModel::encryptFile
            )

            MenuButton(R.string.select_file) {
                fileLauncher.launch(arrayOf("*/*"))
            }
        }
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
        style = MaterialTheme.typography.button
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
            backgroundColor = MaterialTheme.colors.error
        ) else ButtonDefaults.buttonColors(),
    ) {
        Text(
            text = logName ?: stringResource(id = R.string.select_log),
            style = MaterialTheme.typography.button
        )
    }

    if (viewModel.showSelectLogDialog.collectAsState().value) SelectLogDialog()
}

@Composable
private fun SelectLogDialog() {
    val viewModel = viewModel<EncryptViewModel>()

    val logFileMimeType = "*/*"

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
        viewModel::onNewLogFileSelected
    )

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(logFileMimeType),
        viewModel::onNewLogFileSelected
    )

    Dialog(onDismissRequest = viewModel::hideSelectLogDialog) {
        Surface(shape = MaterialTheme.shapes.small) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {

                val knownLogs by viewModel.knownLogs.collectAsState()

                TextItemList(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .heightIn(min = 160.dp, max = 320.dp),
                    items = knownLogs,
                    onItemClicked = { viewModel.onKnownLogFileSelected(it) },
                    itemText = { it.name }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(id = R.string.open)
                    ) {
                        openLauncher.launch(arrayOf(logFileMimeType))
                    }

                    Spacer(modifier = Modifier.size(16.dp))

                    ActionButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(id = R.string.create)
                    ) {
                        createLauncher.launch(
                            "${AppProperties.DEFAULT_LOG_FILE_NAME}.${AppProperties.LOG_FILE_EXTENSION}"
                        )
                    }
                }
            }
        }
    }
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
        style = MaterialTheme.typography.button
    )
}
