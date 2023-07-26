package co.samco.mendroid.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import co.samco.mendroid.R
import co.samco.mendroid.ui.common.TextItemList
import co.samco.mendroid.viewmodel.SelectLogViewModel

@Composable
fun LogList(
    modifier: Modifier,
    navController: NavHostController
) = Column(modifier = modifier.fillMaxSize()) {
    val viewModel = hiltViewModel<SelectLogViewModel>()
    val availableLogs = viewModel.availableLogs.collectAsState().value

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
        viewModel::onOpenLogFromUri
    )

    TextItemList(
        modifier = Modifier.fillMaxWidth().weight(1f),
        items = availableLogs,
        onItemClicked = {
            viewModel.onLogSelected(it)
            navController.navigate(NAV_DECRYPT_LOG_TEXT)
        },
        itemText = { it.name }
    )

    Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = { launcher.launch(arrayOf("*/*")) }
    ) {
        Text(
            text = stringResource(id = R.string.import_log).uppercase(),
            style = MaterialTheme.typography.button
        )
    }
}