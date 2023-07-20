package co.samco.mendroid.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.TextFieldColors
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColors(
    primary = Green1,
    primaryVariant = Green3,
    secondary = Gray1,
    secondaryVariant = Gray3,
    background = Black2,
    surface = Charcoal1,
    error = Red1,
    onPrimary = Black1,
    onSecondary = White1,
    onBackground = White1,
    onSurface = White1,
    onError = White1,
)

private val LightColorScheme = lightColors(
    primary = Green3,
    primaryVariant = Green2,
    secondary = Gray1,
    secondaryVariant = Gray2,
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
fun mendTextFieldColors(): TextFieldColors {
  return TextFieldDefaults.textFieldColors(
      cursorColor = MaterialTheme.colors.onSurface,
  )
}

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
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colors = colorScheme,
        typography = Typography,
        content = content
    )
}