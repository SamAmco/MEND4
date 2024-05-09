package co.samco.mendroid.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import co.samco.mendroid.R
import co.samco.mendroid.ui.theme.MEND4Theme
import co.samco.mendroid.viewmodel.AudioRecordingViewModel
import co.samco.mendroid.viewmodel.AudioRecordingViewModelImpl
import co.samco.mendroid.viewmodel.TestRecordingState

@Composable
fun RecordAudioDialog() {
    val viewModel: AudioRecordingViewModel = viewModel<AudioRecordingViewModelImpl>()

    Dialog(
        onDismissRequest = { viewModel.dismissRecordAudioDialog() },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        RecordAudioDialogView(
            loading = viewModel.loading,
            isRecording = viewModel.isRecording.collectAsState().value,
            timeText = viewModel.timeText.collectAsState().value,
            hasRecording = viewModel.hasRecording.collectAsState().value,
            dismissRecordAudioDialog = viewModel::dismissRecordAudioDialog,
            startRecording = viewModel::startRecording,
            stopRecording = viewModel::stopRecording,
            retryRecording = viewModel::retryRecording,
            saveRecording = viewModel::saveRecording,
            testRecordingState = viewModel.testRecordingState.collectAsState().value,
            startTestRecording = viewModel::startTestRecording,
            stopTestRecording = viewModel::stopTestRecording,
            finishTestRecording = viewModel::finishTestRecording
        )
    }
}

@Composable
private fun RecordAudioDialogView(
    loading: Boolean,
    isRecording: Boolean,
    timeText: String,
    hasRecording: Boolean,
    dismissRecordAudioDialog: () -> Unit,
    startRecording: () -> Unit,
    stopRecording: () -> Unit,
    retryRecording: () -> Unit,
    saveRecording: () -> Unit,
    testRecordingState: TestRecordingState,
    startTestRecording: () -> Unit,
    stopTestRecording: () -> Unit,
    finishTestRecording: () -> Unit
) = Surface(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 32.dp)
) {
    Box(
        modifier = Modifier.defaultMinSize(minHeight = 160.dp),
        contentAlignment = Alignment.Center
    ) {
        CloseButton(
            enabled = !loading,
            onClose = dismissRecordAudioDialog
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 48.dp)
        ) {
            Spacer(modifier = Modifier.height(38.dp))

            TestRecordingControls(
                testRecordingState = testRecordingState,
                onTestButtonPressed = startTestRecording,
                onTestButtonReleased = stopTestRecording,
                onCancelButtonPressed = finishTestRecording
            )

            Spacer(modifier = Modifier.height(16.dp))

            TimeText(
                timeText = timeText,
                fullAlpha = isRecording
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (loading) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (hasRecording) RetrySaveButtons(
                retryRecording = retryRecording,
                saveRecording = saveRecording,
                loading = loading
            )
            else RecordStopButtons(
                isRecording = isRecording,
                startRecording = startRecording,
                stopRecording = stopRecording,
                loading = loading,
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TestRecordingControls(
    testRecordingState: TestRecordingState,
    onTestButtonPressed: () -> Unit,
    onTestButtonReleased: () -> Unit,
    onCancelButtonPressed: () -> Unit
) {
    val color =
        if (testRecordingState == TestRecordingState.RECORDING) MaterialTheme.colors.primaryVariant
        else MaterialTheme.colors.primary
    Surface(
        modifier = Modifier.animateContentSize(),
        shape = CircleShape,
        color = color
    ) {
        val isPlayingBack = remember(testRecordingState) {
            testRecordingState == TestRecordingState.PLAYING_BACK
        }

        Row(
            modifier = Modifier
                .pointerInput(isPlayingBack) {
                    if (testRecordingState == TestRecordingState.PLAYING_BACK) {
                        detectTapGestures(onTap = { onCancelButtonPressed() })
                    } else {
                        awaitEachGesture {
                            awaitFirstDown().consume()
                            onTestButtonPressed()
                            waitForUpOrCancellation()?.consume()
                            onTestButtonReleased()
                        }
                    }
                }
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            val text = when (testRecordingState) {
                TestRecordingState.WAITING -> stringResource(R.string.hold_to_test)
                TestRecordingState.RECORDING -> stringResource(R.string.recording)
                TestRecordingState.PLAYING_BACK -> stringResource(R.string.playing_back)
            }
            Text(
                modifier = Modifier.padding(bottom = 2.dp),
                text = text,
                style = MaterialTheme.typography.body1,
            )

            Spacer(modifier = Modifier.width(8.dp))

            when (testRecordingState) {
                TestRecordingState.WAITING -> RecordButtonContent(size = 14.dp)
                TestRecordingState.RECORDING -> {}
                TestRecordingState.PLAYING_BACK -> StopButtonContent(size = 14.dp)
            }
        }
    }
}

@Composable
private fun TimeText(timeText: String, fullAlpha: Boolean) {
    Text(
        text = timeText,
        style = MaterialTheme.typography.h3.copy(
            color = MaterialTheme.colors.onSurface.copy(
                alpha = if (fullAlpha) 1f else 0.5f
            )
        ),
    )
}

@Composable
private fun BoxScope.CloseButton(
    enabled: Boolean,
    onClose: () -> Unit
) {
    IconButton(
        modifier = Modifier.Companion.align(Alignment.TopEnd),
        enabled = enabled,
        onClick = { onClose() }
    ) { CloseButtonContent(24.dp) }
}

@Composable
private fun CloseButtonContent(size: Dp) {
    Icon(
        modifier = Modifier.size(size),
        imageVector = Icons.Filled.Close,
        contentDescription = null
    )
}

@Composable
private fun RecordStopButtons(
    isRecording: Boolean,
    startRecording: () -> Unit,
    stopRecording: () -> Unit,
    loading: Boolean
) {
    if (isRecording) {
        IconButton(
            onClick = { stopRecording() },
            enabled = !loading
        ) { StopButtonContent(size = 32.dp) }
    } else {
        IconButton(
            onClick = { startRecording() },
            enabled = !loading
        ) { RecordButtonContent(size = 32.dp) }
    }
}

@Composable
private fun RetrySaveButtons(
    retryRecording: () -> Unit,
    saveRecording: () -> Unit,
    loading: Boolean
) = Row {
    IconButton(
        onClick = { retryRecording() },
        enabled = !loading
    ) { RetryButtonContent(size = 32.dp) }

    Spacer(modifier = Modifier.width(32.dp))

    IconButton(
        onClick = { saveRecording() },
        enabled = !loading
    ) { SaveButtonContent(size = 32.dp) }
}

@Composable
private fun RecordButtonContent(
    modifier: Modifier = Modifier,
    size: Dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(
                color = MaterialTheme.colors.error,
                shape = CircleShape
            )
    )
}

@Composable
private fun StopButtonContent(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .aspectRatio(1f)
            .background(
                color = MaterialTheme.colors.onSurface,
                shape = RectangleShape
            )
    )
}

@Composable
private fun SaveButtonContent(size: Dp) {
    Icon(
        modifier = Modifier.size(size),
        imageVector = Icons.Filled.Check,
        contentDescription = null
    )
}

@Composable
private fun RetryButtonContent(size: Dp) {
    Icon(
        modifier = Modifier.size(size),
        imageVector = Icons.Filled.Replay,
        contentDescription = null
    )
}

@Preview
@Composable
fun RecordAudioDialogPreview() {
    MEND4Theme {
        RecordAudioDialogView(
            loading = false,
            isRecording = false,
            timeText = "00:00",
            hasRecording = false,
            dismissRecordAudioDialog = {},
            startRecording = {},
            stopRecording = {},
            retryRecording = {},
            saveRecording = {},
            testRecordingState = TestRecordingState.PLAYING_BACK,
            startTestRecording = {},
            stopTestRecording = {},
            finishTestRecording = {}
        )
    }
}