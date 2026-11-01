package co.samco.mendroid.ui.common

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun BoxScope.Divider() = HorizontalDivider(
    modifier = Modifier.align(Alignment.BottomCenter),
    color = MaterialTheme.colorScheme.outlineVariant
)
