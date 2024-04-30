package co.samco.mendroid

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import co.samco.mendroid.model.ErrorToastManager
import co.samco.mendroid.model.PropertyManager
import co.samco.mendroid.model.Theme
import co.samco.mendroid.ui.screens.HomeScreen
import co.samco.mendroid.ui.screens.SettingsScreen
import co.samco.mendroid.ui.theme.MEND4Theme
import co.samco.mendroid.viewmodel.HomeViewModel
import co.samco.mendroid.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var errorToastManager: ErrorToastManager

    @Inject
    lateinit var propertyManager: PropertyManager

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        checkNotificationsPermission()

        collectToasts()

        setContent {
            val selectedTheme = propertyManager.selectedTheme
                .collectAsState(null).value ?: isSystemInDarkTheme()
            MEND4Theme(darkTheme = selectedTheme == Theme.DARK) { Mend4App() }
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Mend4App() {
    val settingsViewModel = viewModel<SettingsViewModel>()

    val homeViewModel = viewModel<HomeViewModel>()
    val selectedTabIndex = homeViewModel.state.collectAsState().value.index

    val showSettings = settingsViewModel.showSettings.collectAsState().value

    val focusRequester = remember { FocusRequester() }
    val localFocusManager = LocalFocusManager.current
    val localKeyboardController = LocalSoftwareKeyboardController.current

    HomeScreenScaffold(
        focusRequester = focusRequester,
        selectedTabIndex = selectedTabIndex
    )

    AnimatedVisibility(
        visible = showSettings,
        enter = fadeIn() + slideIn(initialOffset = { IntOffset(it.width, -it.height) }),
        exit = fadeOut() + slideOut(targetOffset = { IntOffset(it.width, -it.height) })
    ) {
        SettingsScreen()
    }

    LaunchedEffect(showSettings, selectedTabIndex) {
        if (!showSettings) {
            focusRequester.requestFocus()
        } else {
            localFocusManager.clearFocus()
            localKeyboardController?.hide()
        }
    }
}

@Composable
fun HomeScreenScaffold(
    focusRequester: FocusRequester,
    selectedTabIndex: Int
) {
    val navController = rememberNavController()
    Scaffold(
        topBar = {
            Mend4TopAppBar(navController)
        },
        content = {
            HomeScreen(
                modifier = Modifier.padding(it),
                navHostController = navController,
                selectedTabIndex = selectedTabIndex,
                focusRequester = focusRequester
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