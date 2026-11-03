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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import co.samco.mendroid.R
import co.samco.mendroid.ui.common.ConfirmCancelDialogBody
import co.samco.mendroid.ui.common.Divider
import co.samco.mendroid.ui.common.DateScrollLazyColumn
import co.samco.mendroid.viewmodel.DecryptFileDialogData
import co.samco.mendroid.viewmodel.DecryptedLogViewModel
import co.samco.mendroid.viewmodel.LogViewData
import co.samco.mendroid.viewmodel.TextType
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun DecryptLogText(modifier: Modifier) {
    val viewModel = hiltViewModel<DecryptedLogViewModel>()

    val decryptingLog = viewModel.decryptingLog.collectAsState().value
    val decryptingFile = viewModel.decryptingFile.collectAsState().value
    val decryptFileDialogData = viewModel.decryptFileDialogData.collectAsState().value

    if (decryptingFile) {
        Dialog(onDismissRequest = { viewModel.onCancelDecryptFileClicked() }) {
            DecryptingFileDialog(viewModel.downloadingFile.collectAsState().value, viewModel::onCancelDecryptFileClicked)
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
        Column(modifier = modifier) {
            val logLines = viewModel.logLines.collectAsState().value

            if (logLines.isEmpty()) EmptyLinesText()
            else LogLines(logLines = logLines)

            SearchField(logLines = logLines)
        }

    }
}

@Composable
private fun ColumnScope.EmptyLinesText() = Box(
    modifier = Modifier
        .fillMaxWidth()
        .weight(1f),
    contentAlignment = Alignment.Center
) {
    Text(
        modifier = Modifier.alpha(0.5f),
        text = stringResource(id = R.string.no_entries),
        style = MaterialTheme.typography.titleMedium
    )
}


@Composable
private fun DecryptingFileDialog(downloading: Boolean, cancel: () -> Unit) = Column(
    modifier = Modifier.background(MaterialTheme.colorScheme.background),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
) {
    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
    Text(
        modifier = Modifier.padding(16.dp),
        text = stringResource(if (downloading) R.string.downloading_encrypted_file else R.string.decrypting_file),
        style = MaterialTheme.typography.titleLarge
    )
    TextButton(onClick = cancel) { Text(stringResource(R.string.cancel)) }
}

@Composable
private fun DecryptFileDialog(
    decryptFileDialogData: DecryptFileDialogData,
    onCancelClicked: () -> Unit,
    onDecryptClicked: () -> Unit
) = ConfirmCancelDialogBody(
    text = stringResource(
        id = R.string.would_you_like_to_decrypt_file,
        decryptFileDialogData.fileName
    ),
    cancelText = stringResource(id = R.string.cancel),
    confirmText = stringResource(id = R.string.decrypt),
    onCancelClicked = onCancelClicked,
    onConfirmClicked = onDecryptClicked
)

@Composable
private fun SearchField(logLines: List<LogViewData>) = Row(
    modifier = Modifier
        .background(MaterialTheme.colorScheme.background)
        .fillMaxWidth()
) {
    val viewModel = hiltViewModel<DecryptedLogViewModel>()

    val focusRequester = remember { FocusRequester() }

    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        value = viewModel.searchText,
        onValueChange = { viewModel.searchText = it },
        placeholder = { Text(text = stringResource(id = R.string.filter)) },
        trailingIcon = {
            Checkbox(
                checked = viewModel.filterEnabled,
                onCheckedChange = {
                    if (!it) {
                        focusManager.clearFocus()
                        viewModel.onSearchComplete()
                    } else focusRequester.requestFocus()

                    viewModel.filterEnabled = it
                }
            )
        },
        maxLines = 1,
        keyboardActions = KeyboardActions(
            onDone = {
                focusManager.clearFocus()
                viewModel.onSearchComplete()
            }
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        isError = viewModel.filterEnabled && logLines.isEmpty()
    )

    LaunchedEffect(Unit) {
        if (!viewModel.searchComplete) focusRequester.requestFocus()
    }
}

@Composable
private fun ColumnScope.LogLines(logLines: List<LogViewData>) = SelectionContainer(
    modifier = Modifier.weight(1f)
) {
    val viewModel = hiltViewModel<DecryptedLogViewModel>()

    val listState = rememberLazyListState(
        viewModel.firstVisibleItemIndex,
        viewModel.firstVisibleItemScrollOffset
    )

    DisposableEffect(listState) {
        onDispose {
            viewModel.storeListState(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
    }

    LaunchedEffect(key1 = logLines) {
        viewModel.scrollToIndex.collect {
            listState.animateScrollToItem(it)
        }
    }

    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
    val unknownDate = stringResource(R.string.unknown_date)

    DateScrollLazyColumn(
        state = listState,
        itemCount = logLines.size,
        dateLabel = { index -> logLines.getOrNull(index)?.date?.format(dateFormatter) ?: unknownDate }
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
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
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
            val link = LinkAnnotation.Clickable(
                tag = textPart.type.name,
                linkInteractionListener = {
                    if (textPart.type == TextType.FILE_ID) {
                        viewModel.onFileIdClicked(textPart.text)
                    } else {
                        val press = PressInteraction.Press(Offset.Zero)
                        cardInteractionSource.tryEmit(press)
                        cardInteractionSource.tryEmit(PressInteraction.Release(press))
                        viewModel.onLogLineClicked(logLine)
                    }
                }
            )

            when (textPart.type) {
                TextType.PLAIN -> withStyle(style = plainTextSpanStyle()) {
                    withLink(link) { append(textPart.text) }
                }

                TextType.FILE_ID -> withStyle(style = fileIdSpanStyle()) {
                    withLink(link) { append(textPart.text) }
                }
            }

        }
    }
    Text(
        modifier = Modifier.padding(vertical = 16.dp, horizontal = 16.dp),
        text = annotatedString
    )
}

@Composable
private fun plainTextSpanStyle() = SpanStyle(
    color = MaterialTheme.colorScheme.onBackground,
    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
    fontWeight = MaterialTheme.typography.bodyLarge.fontWeight,
    fontStyle = MaterialTheme.typography.bodyLarge.fontStyle,
    fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
    letterSpacing = MaterialTheme.typography.bodyLarge.letterSpacing,
)

@Composable
private fun fileIdSpanStyle() = SpanStyle(
    color = MaterialTheme.colorScheme.onPrimary,
    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
    fontWeight = MaterialTheme.typography.bodyLarge.fontWeight,
    fontStyle = MaterialTheme.typography.bodyLarge.fontStyle,
    fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
    letterSpacing = MaterialTheme.typography.bodyLarge.letterSpacing,
    background = MaterialTheme.colorScheme.primary
)
