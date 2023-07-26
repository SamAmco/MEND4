package co.samco.mendroid.ui.screens

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import co.samco.mendroid.R
import co.samco.mendroid.ui.common.Divider
import co.samco.mendroid.viewmodel.DecryptFileDialogData
import co.samco.mendroid.viewmodel.DecryptedLogViewModel
import co.samco.mendroid.viewmodel.LogViewData
import co.samco.mendroid.viewmodel.TextType

@Composable
fun DecryptLogText(modifier: Modifier) {
    val viewModel = hiltViewModel<DecryptedLogViewModel>()

    val decryptingLog = viewModel.decryptingLog.collectAsState().value
    val decryptingFile = viewModel.decryptingFile.collectAsState().value
    val decryptFileDialogData = viewModel.decryptFileDialogData.collectAsState().value

    if (decryptingFile) {
        Dialog(onDismissRequest = { viewModel.onCancelDecryptFileClicked() }) {
            DecryptingFileDialog()
        }
    } else if (decryptFileDialogData != null) {
        Dialog(onDismissRequest = { viewModel.onCancelDecryptFileClicked() }) {
            DecryptFileDialog(
                decryptFileDialogData = decryptFileDialogData,
                onCancelClicked = { viewModel.onCancelDecryptFileClicked() },
                onDecryptClicked = { viewModel.onDecryptFileClicked(decryptFileDialogData) }
            )
        }
    }

    if (decryptingLog) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }
    } else {

        val searchFocusRequester = remember { FocusRequester() }

        Column {
            val logLines = viewModel.logLines.collectAsState().value

            if (logLines.isEmpty()) EmptyLinesText()
            else LogLines(logLines = logLines)

            SearchField(logLines = logLines, focusRequester = searchFocusRequester)
        }

        LaunchedEffect(Unit) {
            searchFocusRequester.requestFocus()
        }
    }
}

@Composable
private fun ColumnScope.EmptyLinesText() = Box(
    modifier = Modifier.fillMaxWidth().weight(1f),
    contentAlignment = Alignment.Center
) {
    Text(
        modifier = Modifier.alpha(0.5f),
        text = stringResource(id = R.string.no_entries),
        style = MaterialTheme.typography.subtitle1
    )
}


@Composable
private fun DecryptingFileDialog() = Column(
    modifier = Modifier.background(MaterialTheme.colors.background),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
) {
    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
    Text(
        modifier = Modifier.padding(16.dp),
        text = stringResource(id = R.string.decrypting_file),
        style = MaterialTheme.typography.h6
    )
}

@Composable
private fun DecryptFileDialog(
    decryptFileDialogData: DecryptFileDialogData,
    onCancelClicked: () -> Unit,
    onDecryptClicked: () -> Unit
) = Column(
    modifier = Modifier.background(MaterialTheme.colors.background),
) {
    Text(
        modifier = Modifier.padding(16.dp),
        text = stringResource(
            id = R.string.would_you_like_to_decrypt_file,
            decryptFileDialogData.fileName
        ),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(
            onClick = onCancelClicked,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colors.onBackground.copy(alpha = 0.5f)
            )
        ) {
            Text(text = stringResource(id = R.string.cancel).uppercase())
        }
        TextButton(onClick = onDecryptClicked) {
            Text(text = stringResource(id = R.string.decrypt).uppercase())
        }
    }
}

@Composable
private fun SearchField(logLines: List<LogViewData>, focusRequester: FocusRequester) = Row(
    modifier = Modifier
        .background(MaterialTheme.colors.background)
        .fillMaxWidth()
) {
    val viewModel = hiltViewModel<DecryptedLogViewModel>()

    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .let {
                if (viewModel.filterEnabled && logLines.isEmpty()) {
                    it.background(MaterialTheme.colors.error)
                } else it
            },
        value = viewModel.searchText,
        onValueChange = { viewModel.searchText = it },
        placeholder = { Text(text = stringResource(id = R.string.filter)) },
        trailingIcon = {
            Checkbox(
                checked = viewModel.filterEnabled,
                onCheckedChange = {
                    if (!it) focusManager.clearFocus()
                    else focusRequester.requestFocus()

                    viewModel.filterEnabled = it
                }
            )
        },
        maxLines = 1,
    )
}

@Composable
private fun ColumnScope.LogLines(logLines: List<LogViewData>) = SelectionContainer(
    modifier = Modifier.weight(1f)
) {
    val viewModel = hiltViewModel<DecryptedLogViewModel>()

    val listState = rememberLazyListState()

    LaunchedEffect(key1 = logLines) {
        viewModel.scrollToIndex.collect {
            listState.animateScrollToItem(it)
        }
    }

    LazyColumn(
        modifier = Modifier.padding(),
        state = listState
    ) {
        items(logLines.size) { index ->

            val interactionSource = remember { MutableInteractionSource() }

            Box(modifier = if (viewModel.filterEnabled) {
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = interactionSource,
                        LocalIndication.current,
                        onClick = { viewModel.onLogLineClicked(logLines[index]) }
                    )
            } else Modifier.fillMaxWidth()) {
                Column {
                    val dateTime = logLines[index].dateTime
                    if (dateTime != null) {
                        Text(
                            modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp),
                            text = dateTime,
                            style = MaterialTheme.typography.subtitle2.copy(
                                color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f)
                            )
                        )
                    }

                    LogText(
                        logLine = logLines[index],
                        cardInteractionSource = interactionSource
                    )
                }

                if (index < logLines.size - 1) Divider()
            }
        }
    }
}

@Composable
private fun LogText(logLine: LogViewData, cardInteractionSource: MutableInteractionSource) {
    val viewModel = hiltViewModel<DecryptedLogViewModel>()

    val annotatedString = buildAnnotatedString {
        for (textPart in logLine.text) {

            when (textPart.type) {
                TextType.PLAIN -> withStyle(style = plainTextSpanStyle()) {
                    pushStringAnnotation(
                        tag = "type",
                        annotation = textPart.type.name
                    )
                    append(textPart.text)
                }

                TextType.FILE_ID -> withStyle(style = fileIdSpanStyle()) {
                    pushStringAnnotation(
                        tag = "type",
                        annotation = textPart.type.name
                    )
                    append(textPart.text)
                }
            }

        }
    }
    ClickableText(
        modifier = Modifier.padding(vertical = 16.dp, horizontal = 16.dp),
        text = annotatedString,
        onClick = { offset ->
            annotatedString.getStringAnnotations(offset, offset)
                .firstOrNull()?.let { annotation ->
                    //Because clickable text won't let me filter which parts of the text
                    // are actually clickable i have to pass the event to the parent card
                    // and view model if it's not a file id clicked on.
                    if (annotation.item == TextType.FILE_ID.name) {
                        val fileId = annotatedString.text
                            .slice(annotation.start until annotation.end)
                        viewModel.onFileIdClicked(fileId)
                    } else {
                        val press = PressInteraction.Press(Offset.Zero)
                        cardInteractionSource.tryEmit(press)
                        cardInteractionSource.tryEmit(PressInteraction.Release(press))
                        viewModel.onLogLineClicked(logLine)
                    }
                }
        }
    )
}

@Composable
private fun plainTextSpanStyle() = SpanStyle(
    color = MaterialTheme.colors.onBackground,
    fontSize = MaterialTheme.typography.body1.fontSize,
    fontWeight = MaterialTheme.typography.body1.fontWeight,
    fontStyle = MaterialTheme.typography.body1.fontStyle,
    fontFamily = MaterialTheme.typography.body1.fontFamily,
    letterSpacing = MaterialTheme.typography.body1.letterSpacing,
)

@Composable
private fun fileIdSpanStyle() = SpanStyle(
    color = MaterialTheme.colors.onPrimary,
    fontSize = MaterialTheme.typography.body1.fontSize,
    fontWeight = MaterialTheme.typography.body1.fontWeight,
    fontStyle = MaterialTheme.typography.body1.fontStyle,
    fontFamily = MaterialTheme.typography.body1.fontFamily,
    letterSpacing = MaterialTheme.typography.body1.letterSpacing,
    background = MaterialTheme.colors.primary
)
