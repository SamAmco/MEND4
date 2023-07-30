package co.samco.mendroid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.samco.mendroid.R
import co.samco.mendroid.viewmodel.UnlockViewModel

@Composable
fun UnlockScreen(
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester
) = Column(
    modifier = modifier.fillMaxSize(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
) {
    val unlockViewModel = viewModel<UnlockViewModel>()

    OutlinedTextField(
        modifier = Modifier
            .width(220.dp)
            .focusRequester(focusRequester),
        textStyle = MaterialTheme.typography.body1.copy(textAlign = TextAlign.Center),
        value = unlockViewModel.password,
        onValueChange = { unlockViewModel.password = it },
        visualTransformation = PasswordVisualTransformation(),
        trailingIcon = if (unlockViewModel.unlocking) {
            {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
            }
        } else null,
        placeholder = {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(id = R.string.password),
                textAlign = TextAlign.Center,
            )
        },
        maxLines = 1,
        enabled = !unlockViewModel.unlocking,
        keyboardOptions = KeyboardOptions.Default.copy(
            imeAction = ImeAction.Done,
            keyboardType = KeyboardType.Password
        ),
        keyboardActions = KeyboardActions(
            onDone = { unlockViewModel.onUnlockPressed() }
        )
    )

    Spacer(modifier = Modifier.width(16.dp))

    Button(
        onClick = unlockViewModel::onUnlockPressed,
        enabled = !unlockViewModel.unlocking
    ) {
        Text(
            text = stringResource(id = R.string.unlock).uppercase(),
            style = MaterialTheme.typography.button,
        )
    }
}

