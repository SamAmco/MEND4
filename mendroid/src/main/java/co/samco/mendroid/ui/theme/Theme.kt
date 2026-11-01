package co.samco.mendroid.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Green1,
    primaryContainer = Green3,
    secondary = Gray1,
    secondaryContainer = Gray3,
    background = Black2,
    surface = Charcoal1,
    error = Red1,
    onPrimary = Black1,
    onSecondary = White1,
    onBackground = White1,
    onSurface = White1,
    onError = White1,
)

private val LightColorScheme = lightColorScheme(
    primary = Green3,
    primaryContainer = Green2,
    secondary = Gray1,
    secondaryContainer = Gray2,
    background = White2,
    surface = White1,
    error = Red1,
    onPrimary = Black1,
    onSecondary = White1,
    onBackground = Black1,
    onSurface = Black1,
    onError = Black1,
)

@Composable
fun mendTextFieldColors(): TextFieldColors = TextFieldDefaults.colors(
    cursorColor = MaterialTheme.colorScheme.onSurface,
)

@Composable
fun mendTextButtonColors(): ButtonColors = ButtonDefaults.textButtonColors(
    contentColor = MaterialTheme.colorScheme.onSurface
)

@Composable
fun MEND4Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
