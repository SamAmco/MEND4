package co.samco.mendroid.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import co.samco.mendroid.viewmodel.UnlockViewModel

@Composable
fun DecryptScreen(
    modifier: Modifier = Modifier,
    navHostController: NavHostController
) {
    val unlockViewModel = viewModel<UnlockViewModel>()
    val unlocked = unlockViewModel.unlocked.collectAsState().value

    LaunchedEffect(unlocked) {
        if (!unlocked) {
            navHostController.popBackStack(NAV_LOG_LIST, inclusive = false)
        }
    }

    Crossfade(targetState = unlocked) {
        if (it) DecryptLogScreen(
            modifier = modifier,
            navController = navHostController
        ) else UnlockScreen(
            modifier = modifier,
        )
    }
}