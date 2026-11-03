package co.samco.mendroid.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavHostController
import co.samco.mendroid.ui.common.TextItemList
import co.samco.mendroid.viewmodel.SelectLogViewModel

@Composable
fun LogList(
    modifier: Modifier,
    navController: NavHostController
) = Column(modifier = modifier.fillMaxSize()) {
    val viewModel = hiltViewModel<SelectLogViewModel>()
    val availableLogs = viewModel.availableLogs.collectAsState().value
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME, onEvent = viewModel::refreshLogs)

    TextItemList(
        modifier = Modifier.fillMaxWidth(),
        items = availableLogs,
        onItemClicked = { log ->
            viewModel.onLogSelected(log) { navController.navigate(NAV_DECRYPT_LOG_TEXT) }
        },
        itemText = { it.name }
    )
}
