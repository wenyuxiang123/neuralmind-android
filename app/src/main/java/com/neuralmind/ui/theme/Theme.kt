package com.neuralmind.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2196F3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    onPrimaryContainer = Color(0xFF0D47A1),
    secondary = Color(0xFF03DAC6),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF004D40),
    tertiary = Color(0xFF7C4DFF),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE7F6),
    onTertiaryContainer = Color(0xFF311B92),
    error = Color(0xFFB00020),
    onError = Color.White,
    errorContainer = Color(0xFFFCD8DF),
    onErrorContainer = Color(0xFF60141D),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D47A1),
    primaryContainer = Color(0xFF1565C0),
    onPrimaryContainer = Color(0xFFE3F2FD),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF004D40),
    secondaryContainer = Color(0xFF00695C),
    onSecondaryContainer = Color(0xFFB2DFDB),
    tertiary = Color(0xFFB388FF),
    onTertiary = Color(0xFF311B92),
    tertiaryContainer = Color(0xFF4527A0),
    onTertiaryContainer = Color(0xFFEDE7F6),
    error = Color(0xFFCF6679),
    onError = Color(0xFF60141D),
    errorContainer = Color(0xFFB00020),
    onErrorContainer = Color(0xFFFCD8DF),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE1E1E1),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE1E1E1),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    scrim = Color(0xFF000000),
)

@Composable
fun NeuralMindTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        DarkColors
    } else {
        LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
