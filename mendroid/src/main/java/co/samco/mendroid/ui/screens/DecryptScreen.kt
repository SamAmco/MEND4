package co.samco.mendroid.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import co.samco.mendroid.viewmodel.UnlockViewModel

@Composable
fun DecryptScreen(modifier: Modifier = Modifier) {
    val unlockViewModel = viewModel<UnlockViewModel>()

    if (unlockViewModel.privateKey == null) UnlockScreen(modifier)
    else DecryptLogScreen(modifier)
}