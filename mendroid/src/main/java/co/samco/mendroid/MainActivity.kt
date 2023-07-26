package co.samco.mendroid

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import co.samco.mendroid.model.ErrorToastManager
import co.samco.mendroid.model.PrivateKeyManager
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
    lateinit var privateKeyManager: PrivateKeyManager

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkNotificationsPermission()

        collectToasts()

        setContent {
            val selectedTheme = propertyManager.selectedTheme.collectAsState(null).value
            if (selectedTheme != null) {
                MEND4Theme(darkTheme = selectedTheme == Theme.DARK) { Mend4App() }
            } else {
                MEND4Theme { Mend4App() }
            }
        }
    }

    private fun checkNotificationsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (!hasPermission(permission)) requestPermissionLauncher.launch(permission)
        }
    }

    @Suppress("SameParameterValue")
    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun collectToasts() {
        lifecycleScope.launch {
            errorToastManager.errorToast.collect {
                val text = getString(it.messageId, *it.formatArgs.toTypedArray())
                Toast.makeText(this@MainActivity, text, Toast.LENGTH_SHORT).show()
            }
        }
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
    val navController = rememberNavController()
    Scaffold(
        topBar = {
            Mend4TopAppBar(navController)
        },
        content = {
            HomeScreen(
                modifier = Modifier.padding(it),
                navHostController = navController
            )
        }
    )
}

@Composable
fun Mend4TopAppBar(navController: NavController) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    val hasBackAction by remember(currentBackStackEntry) {
        derivedStateOf {
            navController.previousBackStackEntry != null
        }
    }

    TopAppBar(
        title = {
            Text(text = stringResource(id = R.string.app_name) + " " + BuildConfig.VERSION_NAME)
        },
        navigationIcon = if (hasBackAction) {
            {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = stringResource(id = R.string.back)
                    )
                }
            }
        } else null,
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