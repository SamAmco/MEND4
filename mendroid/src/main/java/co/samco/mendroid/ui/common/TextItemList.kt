package co.samco.mendroid.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun <T> TextItemList(
    modifier: Modifier,
    items: List<T>,
    onItemClicked: (T) -> Unit,
    itemText: (T) -> String
) {
    LazyColumn(modifier = modifier) {
        items(items.size) { index ->
            Box(
                modifier = Modifier.clickable {
                    onItemClicked(items[index])
                }
            ) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = itemText(items[index]),
                    style = MaterialTheme.typography.subtitle1
                )

                Divider()
            }
        }
    }
}

