package co.samco.mendroid.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import co.samco.mendroid.R
import co.samco.mendroid.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    modifier: Modifier,
    navHostController: NavHostController,
    selectedTabIndex: Int,
    focusRequester: FocusRequester
) = Column(modifier.padding(8.dp)) {
    val homeViewModel = viewModel<HomeViewModel>()

    TabRow(
        backgroundColor = MaterialTheme.colors.background,
        contentColor = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
        selectedTabIndex = selectedTabIndex
    ) {
        Tab(
            text = { TabText(stringResource(id = R.string.tab_enc)) },
            selected = selectedTabIndex == 0,
            onClick = { homeViewModel.onUserClickedEncrypt() }
        )
        Tab(
            text = { TabText(stringResource(id = R.string.tab_dec)) },
            selected = selectedTabIndex == 1,
            onClick = { homeViewModel.onUserClickedDecrypt() }
        )
    }

    Box(modifier = Modifier.weight(1f)) {

        LaunchedEffect(selectedTabIndex) {
            if (selectedTabIndex == 0) {
                navHostController.popBackStack(NAV_LOG_LIST, false)
            }
        }

        if (selectedTabIndex == 0) {
            EncryptScreen(focusRequester = focusRequester)
        } else {
            DecryptScreen(
                navHostController = navHostController,
                focusRequester = focusRequester
            )
        }
    }
}

@Composable
private fun TabText(text: String) {
    Text(
        text = text.uppercase(),
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.subtitle2
    )
}
