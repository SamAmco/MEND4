package co.samco.mendroid.ui.screens

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
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
    NavHost(
        navController = navController,
        startDestination = NAV_LOG_LIST,
        enterTransition = { slideInHorizontally(tween(250)) { it } },
        exitTransition = { slideOutHorizontally(tween(250)) { -it } },
        popEnterTransition = { slideInHorizontally(tween(250)) { -it } },
        popExitTransition = { slideOutHorizontally(tween(250)) { it } },
        // Predictive back has separate defaults; keep gestures consistent with the back button.
        predictivePopEnterTransition = { slideInHorizontally(tween(250)) { -it } },
        predictivePopExitTransition = { slideOutHorizontally(tween(250)) { it } }
    ) {
        composable(route = NAV_LOG_LIST) {
            LogList(
                modifier = modifier,
                navController = navController
            )
        }
        composable(route = NAV_DECRYPT_LOG_TEXT) {
            DecryptLogText(modifier = modifier)
        }
    }
}
