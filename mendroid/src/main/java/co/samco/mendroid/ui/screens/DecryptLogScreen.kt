package co.samco.mendroid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import co.samco.mendroid.viewmodel.DecryptViewModel

private const val LOG_LIST = "logList"
private const val DECRYPT_LOG_TEXT = "decryptLogText"

@Composable
fun DecryptLogScreen(
    modifier: Modifier = Modifier,
    navController: NavHostController
) {
    NavHost(modifier = modifier, navController = navController, startDestination = LOG_LIST) {
        composable(LOG_LIST) {
            LogList(
                modifier = modifier,
                navController = navController
            )
        }
        composable(DECRYPT_LOG_TEXT) {
            DecryptLogText(
                modifier = modifier,
                navController = navController
            )
        }
    }
}

@Composable
private fun DecryptLogText(
    modifier: Modifier,
    navController: NavHostController
) {
    val viewModel = hiltViewModel<DecryptViewModel>()
    //val logText = viewModel.logText.collectAsState().value

    Text(
        modifier = modifier,
        text = "Log text",
        style = MaterialTheme.typography.body1
    )
}

@Composable
private fun LogList(
    modifier: Modifier,
    navController: NavHostController
) {
    val viewModel = hiltViewModel<DecryptViewModel>()
    val availableLogs = viewModel.availableLogs.collectAsState().value

    LazyColumn(modifier = modifier) {
        items(availableLogs.size) { index ->
            Box(
                modifier = Modifier.clickable { navController.navigate(DECRYPT_LOG_TEXT) }
            ) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = availableLogs[index],
                    style = MaterialTheme.typography.subtitle1
                )
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colors.onBackground.copy(alpha = 0.1f))
                        .height(1.dp)
                        .fillMaxWidth()
                        .align(alignment = Alignment.BottomCenter)
                )
            }
        }
    }
}