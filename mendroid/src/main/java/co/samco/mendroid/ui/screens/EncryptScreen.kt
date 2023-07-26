package co.samco.mendroid.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import co.samco.mend4.core.AppProperties
import co.samco.mendroid.R
import co.samco.mendroid.ui.common.TextItemList
import co.samco.mendroid.ui.theme.mendTextFieldColors
import co.samco.mendroid.viewmodel.EncryptViewModel

@Composable
fun EncryptScreen(
    modifier: Modifier = Modifier,
    navHostController: NavHostController
) = Column(modifier) {

    val viewModel = viewModel<EncryptViewModel>()

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        navHostController.popBackStack(NAV_LOG_LIST, false)
    }

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
            .padding(vertical = 4.dp)
            .height(IntrinsicSize.Min)
    ) {

        SelectLogButton(modifier = Modifier.weight(1f))

        Spacer(modifier = Modifier.size(8.dp))

        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
            viewModel::encryptFile
        )

        ActionButton(text = stringResource(id = R.string.file)) {
            launcher.launch(arrayOf("*/*"))
        }

        Spacer(modifier = Modifier.size(8.dp))

        val currentLog = viewModel.currentLogName.collectAsState().value

        ActionButton(
            text = stringResource(id = R.string.submit),
            enabled = currentLog != null
        ) {
            viewModel.encryptText()
        }
    }
}

@Composable
private fun SelectLogButton(modifier: Modifier) {
    val viewModel = viewModel<EncryptViewModel>()

    val logName by viewModel.currentLogName.collectAsState()

    Button(
        modifier = modifier,
        onClick = viewModel::onSelectLogButtonClicked,
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
                        .height(200.dp),
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
