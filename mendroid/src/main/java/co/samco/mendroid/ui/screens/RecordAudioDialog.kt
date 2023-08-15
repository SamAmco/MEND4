package co.samco.mendroid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Replay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import co.samco.mendroid.viewmodel.AudioRecordingViewModel

@Composable
fun RecordAudioDialog() {
    val viewModel = viewModel<AudioRecordingViewModel>()

    Dialog(
        onDismissRequest = { viewModel.dismissRecordAudioDialog() },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            Box(
                modifier = Modifier.defaultMinSize(minHeight = 160.dp),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    modifier = Modifier.align(Alignment.TopEnd),
                    enabled = !viewModel.loading,
                    onClick = { viewModel.dismissRecordAudioDialog() }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null
                    )
                }

                RecordAudioDialogView()
            }
        }
    }
}

@Composable
private fun RecordAudioDialogView() = Column(
    horizontalAlignment = Alignment.CenterHorizontally
) {
    val viewModel = viewModel<AudioRecordingViewModel>()

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        viewModel.timeText.collectAsState().value,
        style = MaterialTheme.typography.h3.copy(
            color = MaterialTheme.colors.onSurface.copy(
                alpha = if (viewModel.recording) 1f else 0.5f
            )
        ),
    )

    Spacer(modifier = Modifier.height(16.dp))

    if (viewModel.loading) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (viewModel.hasRecording.collectAsState().value) RetrySaveButtons()
    else RecordStopButtons()

    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun RecordStopButtons() {
    val viewModel = viewModel<AudioRecordingViewModel>()

    if (viewModel.recording) {
        IconButton(
            onClick = { viewModel.stopRecording() },
            enabled = !viewModel.loading,
        ) {

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = MaterialTheme.colors.onSurface,
                        shape = RectangleShape
                    )
            )

        }
    } else {
        IconButton(
            onClick = { viewModel.startRecording() },
            enabled = !viewModel.loading,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = MaterialTheme.colors.error,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun RetrySaveButtons() = Row {
    val viewModel = viewModel<AudioRecordingViewModel>()

    IconButton(
        onClick = { viewModel.retryRecording() },
        enabled = !viewModel.loading
    ) {
        Icon(
            modifier = Modifier.size(32.dp),
            imageVector = Icons.Filled.Replay,
            contentDescription = null
        )
    }

    Spacer(modifier = Modifier.width(32.dp))

    IconButton(
        onClick = { viewModel.saveRecording() },
        enabled = !viewModel.loading
    ) {
        Icon(
            modifier = Modifier.size(32.dp),
            imageVector = Icons.Filled.Check,
            contentDescription = null
        )
    }
}