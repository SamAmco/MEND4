package co.samco.mendroid.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavHostController
import co.samco.mendroid.R
import co.samco.mendroid.ui.common.TextItemList
import co.samco.mendroid.viewmodel.DecryptLogItem
import co.samco.mendroid.viewmodel.SelectLogViewModel

@Composable
fun LogList(modifier: Modifier, navController: NavHostController) = Column(modifier.fillMaxSize()) {
    val viewModel = hiltViewModel<SelectLogViewModel>()
    val state = viewModel.state.collectAsState().value
    val resources = LocalResources.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME, onEvent = viewModel::refreshLogs)

    TextButton(onClick = viewModel::refreshLogs, enabled = !state.loading && !state.opening) {
        Text(stringResource(R.string.refresh_logs))
    }
    if (state.loading || state.opening) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (state.opening) {
        Text(stringResource(R.string.opening_log))
        TextButton(onClick = viewModel::cancelOpen) { Text(stringResource(R.string.cancel)) }
    }
    for (problem in state.problems) {
        Text(stringResource(R.string.remote_folder_problem, problem.folder.value,
            stringResource(remoteReadFailureMessage(problem.reason))), color = MaterialTheme.colorScheme.error)
    }
    TextItemList(
        modifier = Modifier.fillMaxWidth().weight(1f),
        items = state.logs,
        onItemClicked = { log -> viewModel.onLogSelected(log) { navController.navigate(NAV_DECRYPT_LOG_TEXT) } },
        itemText = { log -> when (log) {
            is DecryptLogItem.Local -> log.name
            is DecryptLogItem.Nextcloud -> resources.getString(R.string.nextcloud_log_entry, log.name, log.file.path.parent.value)
        } }
    )
}
