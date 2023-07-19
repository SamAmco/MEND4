package co.samco.mendroid.ui.screens

import android.net.Uri
import android.text.TextUtils
import android.widget.TextView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import co.samco.mendroid.R
import co.samco.mendroid.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen() {
    val settingsViewModel = viewModel<SettingsViewModel>()

    val configText = settingsViewModel.configPath.collectAsState().value
    val logDirText = settingsViewModel.logDirText.collectAsState().value
    val encDirText = settingsViewModel.encDirText.collectAsState().value
    val showCloseButton = settingsViewModel.showCloseButton.collectAsState().value

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colors.background
    ) {
        Box {
            if (showCloseButton) {
                IconButton(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    onClick = { settingsViewModel.onUserCloseSettings() }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(id = R.string.close_settings)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center
            ) {
                ButtonSettingRow(
                    complete = settingsViewModel.hasConfig.collectAsState().value,
                    buttonText = stringResource(id = R.string.config_button_text),
                    contract = ActivityResultContracts.OpenDocument(),
                    value = configText,
                    launchArgs = arrayOf("application/xml", "text/xml"),
                    onUriSelected = settingsViewModel::onSetConfig
                )
                Spacer(modifier = Modifier.size(48.dp))
                ButtonSettingRow(
                    complete = settingsViewModel.logDirGood.collectAsState().value,
                    buttonText = stringResource(id = R.string.log_dir_button_text),
                    contract = ActivityResultContracts.OpenDocumentTree(),
                    value = logDirText,
                    onUriSelected = settingsViewModel::onSetLogDir
                )
                Spacer(modifier = Modifier.size(48.dp))
                ButtonSettingRow(
                    complete = settingsViewModel.encDirGood.collectAsState().value,
                    buttonText = stringResource(id = R.string.enc_dir_button_text),
                    contract = ActivityResultContracts.OpenDocumentTree(),
                    value = encDirText,
                    onUriSelected = settingsViewModel::onSetEncDir
                )
                Spacer(modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
fun <T> ButtonSettingRow(
    complete: Boolean,
    buttonText: String,
    value: String? = null,
    contract: ActivityResultContract<in T, out Uri?>,
    launchArgs: T? = null,
    onUriSelected: (Uri?) -> Unit = {}
) {
    val launcher = rememberLauncherForActivityResult(contract, onUriSelected)
    val textColor = MaterialTheme.colors.onSurface.toArgb()

    Row(
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {

        //Compose doesn't support ellipsizing text at the start yet, so we have to use a AndroidView
        AndroidView(
            modifier = Modifier.weight(1f),
            factory = { context ->
                TextView(context).apply {
                    ellipsize = TextUtils.TruncateAt.START
                    setSingleLine()
                    textSize = 16f
                    setTextColor(textColor)
                }
            },
            update = { view ->
                view.text = value ?: view.context.getString(R.string.not_set)
            }
        )

        Spacer(modifier = Modifier.size(16.dp))

        Button(
            onClick = { launcher.launch(launchArgs) }) {
            Text(
                text = buttonText,
                style = MaterialTheme.typography.button
            )
        }
        if (complete) {
            Icon(
                modifier = Modifier
                    .size(48.dp)
                    .padding(8.dp),
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colors.primary,
            )
        } else {
            Icon(
                modifier = Modifier
                    .size(48.dp)
                    .padding(8.dp),
                painter = painterResource(id = R.drawable.cancel),
                contentDescription = null,
                tint = MaterialTheme.colors.error,
            )
        }
    }
}
