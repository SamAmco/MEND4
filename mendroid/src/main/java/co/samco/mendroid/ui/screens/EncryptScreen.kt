package co.samco.mendroid.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.samco.mendroid.R
import co.samco.mendroid.viewmodel.EncryptViewModel

@Composable
fun EncryptScreen(modifier: Modifier = Modifier) = Column(modifier) {

    val viewModel = viewModel<EncryptViewModel>()

    Card(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(8.dp)
            .border(
                border = BorderStroke(1.dp, MaterialTheme.colors.primary),
                shape = MaterialTheme.shapes.small
            ),
    ) {
        TextField(
            value = viewModel.currentEntryText,
            onValueChange = { viewModel.currentEntryText = it },
        )
    }

    Row {
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
        )

        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
            viewModel::encryptFile
        )

        Button(
            onClick = { launcher.launch(arrayOf("*/*")) },
        ) {
            Text(text = stringResource(id = R.string.file))
        }

        Button(
            onClick = { viewModel.encryptText() },
        ) {
            Text(text = stringResource(id = R.string.submit))
        }
    }
}