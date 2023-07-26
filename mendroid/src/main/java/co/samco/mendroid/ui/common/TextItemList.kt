package co.samco.mendroid.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.samco.mendroid.R

@Composable
fun <T> TextItemList(
    modifier: Modifier,
    items: List<T>,
    onItemClicked: (T) -> Unit,
    itemText: (T) -> String
) {
    if (items.isEmpty()) NoItemsText(modifier = modifier)
    else ItemList(
        modifier = modifier,
        items = items,
        onItemClicked = onItemClicked,
        itemText = itemText
    )
}

@Composable
private fun NoItemsText(modifier: Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            modifier = Modifier.alpha(0.5f),
            text = stringResource(id = R.string.no_items),
            style = MaterialTheme.typography.subtitle1
        )
    }
}

@Composable
private fun <T> ItemList(
    modifier: Modifier,
    items: List<T>,
    onItemClicked: (T) -> Unit,
    itemText: (T) -> String
) = LazyColumn(modifier = modifier) {
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
