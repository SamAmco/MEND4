package co.samco.mendroid

import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import co.samco.mendroid.model.ErrorToastManager
import co.samco.mendroid.model.LockEventManager
import co.samco.mendroid.model.PropertyManager
import co.samco.mendroid.model.Theme
import co.samco.mendroid.ui.screens.HomeScreen
import co.samco.mendroid.ui.screens.SettingsScreen
import co.samco.mendroid.ui.theme.MEND4Theme
import co.samco.mendroid.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var errorToastManager: ErrorToastManager

    @Inject
    lateinit var propertyManager: PropertyManager

    @Inject
    lateinit var lockEventManager: LockEventManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val selectedTheme = propertyManager.selectedTheme.collectAsState(null).value
            if (selectedTheme != null) {
                MEND4Theme(darkTheme = selectedTheme == Theme.DARK) { Mend4App() }
            } else {
                MEND4Theme { Mend4App() }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lockEventManager.onActivityStart()
        lifecycleScope.launch {
            errorToastManager.errorToast.collect {
                val text = getString(it.messageId, *it.formatArgs.toTypedArray())
                Toast.makeText(this@MainActivity, text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        lockEventManager.onActivityStop()
    }
}

@Composable
fun Mend4App() {
    val settingsViewModel = viewModel<SettingsViewModel>()

    HomeScreenScaffold()

    if (settingsViewModel.showSettings.collectAsState().value) {
        LocalFocusManager.current.clearFocus()
        SettingsScreen()
    }
}

@Composable
fun HomeScreenScaffold() {
    Scaffold(
        topBar = {
            Mend4TopAppBar()
        },
        content = {
            HomeScreen(Modifier.padding(it))
        }
    )
}

@Composable
fun Mend4TopAppBar() {
    TopAppBar(
        title = {
            Text(text = stringResource(id = R.string.app_name) + " " + BuildConfig.VERSION_NAME)
        },
        actions = {
            val settingsViewModel = viewModel<SettingsViewModel>()
            IconButton(onClick = { settingsViewModel.onUserShowSettings() }) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(id = R.string.settings)
                )
            }
        }
    )
}
