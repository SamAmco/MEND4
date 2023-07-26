package co.samco.mendroid.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.ui.unit.dp

@Composable
fun ConfirmCancelDialogBody(
    text: String,
    cancelText: String,
    confirmText: String,
    onCancelClicked: () -> Unit,
    onConfirmClicked: () -> Unit
) = Column(
    modifier = Modifier.background(MaterialTheme.colors.background),
) {
    Text(
        modifier = Modifier.padding(16.dp),
        text = text,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(
            onClick = onCancelClicked,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colors.onBackground.copy(alpha = 0.5f)
            )
        ) {
            Text(text = cancelText.uppercase())
        }
        TextButton(onClick = onConfirmClicked) {
            Text(text = confirmText.uppercase())
        }
    }
}
