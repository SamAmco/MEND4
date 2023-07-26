package co.samco.mendroid.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

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
