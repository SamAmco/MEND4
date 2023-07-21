package co.samco.mendroid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.CircularProgressIndicator
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

const val NAV_LOG_LIST = "logList"
const val NAV_DECRYPT_LOG_TEXT = "decryptLogText"

@Composable
fun DecryptLogScreen(
    modifier: Modifier = Modifier,
    navController: NavHostController
) {
    NavHost(modifier = modifier, navController = navController, startDestination = NAV_LOG_LIST) {
        composable(NAV_LOG_LIST) {
            LogList(
                modifier = modifier,
                navController = navController
            )
        }
        composable(NAV_DECRYPT_LOG_TEXT) {
            DecryptLogText(modifier = modifier)
        }
    }
}

@Composable
private fun DecryptLogText(modifier: Modifier) {
    val viewModel = hiltViewModel<DecryptViewModel>()
    val logLines = viewModel.logLines.collectAsState().value
    val decryptingLog = viewModel.decryptingLog.collectAsState().value

    if (decryptingLog) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }
    } else {
        SelectionContainer {
            LazyColumn(modifier = modifier.padding(horizontal = 16.dp)) {
                items(logLines.size) { index ->
                    Box {
                        Text(
                            modifier = Modifier.padding(vertical = 16.dp),
                            text = logLines[index],
                            style = MaterialTheme.typography.body1
                        )

                        if (index < logLines.size - 1) Divider()
                    }
                }
            }
        }
    }
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
                modifier = Modifier.clickable {
                    viewModel.onLogSelected(availableLogs[index])
                    navController.navigate(NAV_DECRYPT_LOG_TEXT)
                }
            ) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = availableLogs[index].name,
                    style = MaterialTheme.typography.subtitle1
                )

                Divider()
            }
        }
    }
}

@Composable
private fun BoxScope.Divider() = Box(
    modifier = Modifier
        .background(MaterialTheme.colors.onBackground.copy(alpha = 0.1f))
        .height(1.dp)
        .fillMaxWidth()
        .align(alignment = Alignment.BottomCenter)
)