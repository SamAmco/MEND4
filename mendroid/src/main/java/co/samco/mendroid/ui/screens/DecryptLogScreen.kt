package co.samco.mendroid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import co.samco.mendroid.R
import co.samco.mendroid.viewmodel.DecryptedLogViewModel
import co.samco.mendroid.viewmodel.LogViewData
import co.samco.mendroid.viewmodel.SelectLogViewModel

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
    val viewModel = hiltViewModel<DecryptedLogViewModel>()
    val decryptingLog = viewModel.decryptingLog.collectAsState().value

    if (decryptingLog) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }
    } else {

        val searchFocusRequester = remember { FocusRequester() }

        Column {
            val logLines = viewModel.logLines.collectAsState().value
            LogLines(logLines = logLines)
            SearchField(logLines = logLines, focusRequester = searchFocusRequester)
        }

        LaunchedEffect(Unit) {
            searchFocusRequester.requestFocus()
        }
    }
}

@Composable
private fun SearchField(logLines: List<LogViewData>, focusRequester: FocusRequester) = Row(
    modifier = Modifier
        .background(MaterialTheme.colors.background)
        .fillMaxWidth()
) {
    val viewModel = hiltViewModel<DecryptedLogViewModel>()

    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .let {
                if (viewModel.filterEnabled && logLines.isEmpty()) {
                    it.background(MaterialTheme.colors.error)
                } else it
            },
        value = viewModel.searchText,
        onValueChange = { viewModel.searchText = it },
        placeholder = { Text(text = stringResource(id = R.string.filter)) },
        trailingIcon = {
            Checkbox(
                checked = viewModel.filterEnabled,
                onCheckedChange = {
                    if (!it) focusManager.clearFocus()
                    else focusRequester.requestFocus()

                    viewModel.filterEnabled = it
                }
            )
        },
        maxLines = 1,
    )
}

@Composable
private fun ColumnScope.LogLines(logLines: List<LogViewData>) = SelectionContainer(
    modifier = Modifier.weight(1f)
) {
    val viewModel = hiltViewModel<DecryptedLogViewModel>()

    val listState = rememberLazyListState()

    LaunchedEffect(key1 = logLines) {
        viewModel.scrollToIndex.collect {
            listState.animateScrollToItem(it)
        }
    }

    LazyColumn(
        modifier = Modifier.padding(),
        state = listState
    ) {
        items(logLines.size) { index ->
            Box(modifier = Modifier
                .let {
                    if (viewModel.filterEnabled) it.clickable {
                        viewModel.onLogLineClicked(logLines[index])
                    } else it
                }
            ) {
                Column {
                    val dateTime = logLines[index].dateTime
                    if (dateTime != null) {
                        Text(
                            modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp),
                            text = dateTime,
                            style = MaterialTheme.typography.subtitle2.copy(
                                color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f)
                            )
                        )
                    }

                    Text(
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 16.dp),
                        text = logLines[index].text,
                        style = MaterialTheme.typography.body1
                    )
                }

                if (index < logLines.size - 1) Divider()
            }
        }
    }
}

@Composable
private fun LogList(
    modifier: Modifier,
    navController: NavHostController
) {
    val viewModel = hiltViewModel<SelectLogViewModel>()
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