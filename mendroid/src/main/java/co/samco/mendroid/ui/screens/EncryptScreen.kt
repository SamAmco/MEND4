package co.samco.mendroid.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.samco.mendroid.R
import co.samco.mendroid.viewmodel.EncryptViewModel

@Composable
fun EncryptScreen(modifier: Modifier = Modifier) = Column(modifier) {

    val viewModel = viewModel<EncryptViewModel>()

    Spacer(modifier = Modifier.size(8.dp))

    Card(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .border(
                border = BorderStroke(1.dp, MaterialTheme.colors.primary),
                shape = MaterialTheme.shapes.small
            ),
    ) {
        TextField(
            value = viewModel.currentEntryText,
            onValueChange = { viewModel.currentEntryText = it },
            keyboardOptions = KeyboardOptions.Default.copy(
                capitalization = KeyboardCapitalization.Sentences
            )
        )
    }

    Spacer(modifier = Modifier.size(8.dp))

    Row(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .height(IntrinsicSize.Min)
    ) {
        TextField(
            modifier = Modifier
                .weight(1f)
                .let {
                    return@let if (!viewModel.logNameValid.collectAsState(true).value) {
                        it.background(MaterialTheme.colors.error)
                    } else it
                },
            value = viewModel.currentLogName,
            onValueChange = { viewModel.currentLogName = it },
            maxLines = 1,
        )

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

    Spacer(modifier = Modifier.size(8.dp))
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
