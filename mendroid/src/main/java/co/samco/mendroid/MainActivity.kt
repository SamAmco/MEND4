package co.samco.mendroid

import android.os.Bundle
import android.widget.Space
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.samco.mendroid.ui.theme.MEND4Theme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MEND4Theme {
                Mend4App()
            }
        }
    }
}

@Composable
fun Mend4App() {
    val settingsViewModel = viewModel<SettingsViewModel>()

    HomeScreen()

    if (settingsViewModel.showSettings.collectAsState().value) {
        SettingsScreen()
    }
}

@Composable
fun SettingsScreen() {
    val settingsViewModel = viewModel<SettingsViewModel>()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colors.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp),
            verticalArrangement = Arrangement.Center
        ) {
            ButtonSettingRow(
                complete = settingsViewModel.hasConfig.collectAsState().value,
                buttonText = stringResource(id = R.string.config_button_text),
            )
            Spacer(modifier = Modifier.size(48.dp))
            ButtonSettingRow(
                complete = settingsViewModel.hasLogDir.collectAsState().value,
                buttonText = stringResource(id = R.string.log_dir_button_text),
            )
            Spacer(modifier = Modifier.size(48.dp))
            ButtonSettingRow(
                complete = settingsViewModel.hasEncDir.collectAsState().value,
                buttonText = stringResource(id = R.string.enc_dir_button_text),
            )
        }
    }
}

@Composable
fun ButtonSettingRow(
    complete: Boolean,
    buttonText: String,
    value: String? = null
) {
    Row(
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = "TODO value"
        )

        Button(
            onClick = { /*TODO*/ }) {
            Text(
                text = buttonText,
                style = MaterialTheme.typography.button
            )
        }
        if (complete) {
            Icon(
                modifier = Modifier.height(IntrinsicSize.Max),
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colors.primary,
            )
        } else {
            Icon(
                modifier = Modifier.height(IntrinsicSize.Max),
                painter = painterResource(id = R.drawable.cancel),
                contentDescription = null,
                tint = MaterialTheme.colors.error,
            )
        }
    }
}

@Composable
fun HomeScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colors.background
    ) {
        Text(text = "Hello!")
    }
}