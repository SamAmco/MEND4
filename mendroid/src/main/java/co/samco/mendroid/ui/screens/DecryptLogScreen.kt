package co.samco.mendroid.ui.screens

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

const val NAV_LOG_LIST = "logList"
const val NAV_DECRYPT_LOG_TEXT = "decryptLogText"

@Composable
fun DecryptLogScreen(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    focusRequester: FocusRequester
) {
    NavHost(
        modifier = modifier.focusRequester(focusRequester),
        navController = navController,
        startDestination = NAV_LOG_LIST
    ) {
        composable(
            route = NAV_LOG_LIST,
            exitTransition = {
                slideOutHorizontally { -it }
            },
            popEnterTransition = {
                slideInHorizontally { -it }
            }
        ) {
            LogList(
                modifier = modifier,
                navController = navController
            )
        }
        composable(
            route = NAV_DECRYPT_LOG_TEXT,
            enterTransition = {
                slideInHorizontally { it }
            },
            popExitTransition = {
                slideOutHorizontally { it }
            }
        ) {
            DecryptLogText(modifier = modifier)
        }
    }
}
