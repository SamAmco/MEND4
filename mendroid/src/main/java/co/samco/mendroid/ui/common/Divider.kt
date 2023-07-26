package co.samco.mendroid.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BoxScope.Divider() = Box(
    modifier = Modifier
        .background(MaterialTheme.colors.onBackground.copy(alpha = 0.1f))
        .height(1.dp)
        .fillMaxWidth()
        .align(alignment = Alignment.BottomCenter)
)
