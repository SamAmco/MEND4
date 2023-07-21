package co.samco.mendroid.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import co.samco.mendroid.R
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
        modifier = Modifier.height(IntrinsicSize.Min)
    ) {

        LogNameTextField(modifier = Modifier.weight(1f))

        Spacer(modifier = Modifier.size(8.dp))

        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
            viewModel::encryptFile
        )

        ActionButton(text = stringResource(id = R.string.file)) {
            launcher.launch(arrayOf("*/*"))
        }

        Spacer(modifier = Modifier.size(8.dp))

        ActionButton(text = stringResource(id = R.string.submit)) {
            viewModel.encryptText()
        }
    }
}

@Composable
private fun LogNameTextField(modifier: Modifier) = Box(modifier = modifier) {
    val viewModel = viewModel<EncryptViewModel>()

    var tvFocused by remember { mutableStateOf(false) }
    val nameSuggestions = viewModel.nameSuggestions.collectAsState(emptyList()).value

    TextField(
        modifier = Modifier
            .let {
                return@let if (!viewModel.logNameValid.collectAsState(true).value) {
                    it.background(MaterialTheme.colors.error)
                } else it
            }
            .onFocusChanged { tvFocused = it.isFocused },
        value = viewModel.currentLogName,
        onValueChange = { viewModel.currentLogName = it },
        maxLines = 1,
        colors = mendTextFieldColors()
    )

    DropdownMenu(
        expanded = tvFocused && nameSuggestions.isNotEmpty(),
        onDismissRequest = {},
        properties = PopupProperties(focusable = false)
    ) {
        nameSuggestions.forEach { suggestion ->
            DropdownMenuItem(onClick = {
                viewModel.onNameSuggestionClicked(suggestion)
            }) {
                Text(text = suggestion)
            }
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
) = Button(
    modifier = Modifier
        .fillMaxHeight()
        .padding(vertical = 4.dp),
    onClick = onClick,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.button
    )
}
