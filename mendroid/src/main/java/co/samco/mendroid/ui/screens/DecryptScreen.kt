package co.samco.mendroid.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import co.samco.mendroid.viewmodel.UnlockViewModel

@Composable
fun DecryptScreen(
    modifier: Modifier = Modifier,
    navHostController: NavHostController
) {
    val unlockViewModel = viewModel<UnlockViewModel>()
    val unlocked = unlockViewModel.unlocked.collectAsState(initial = false).value

    if (unlocked) DecryptLogScreen(
        modifier = modifier,
        navController = navHostController
    )
    else UnlockScreen(modifier)
}