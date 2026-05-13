package com.neuralmind.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NeuralMindDarkColors = darkColorScheme(
    // Primary colors - cyan blue gradient
    primary = GradientStart,
    onPrimary = BackgroundPrimary,
    primaryContainer = GradientEnd,
    onPrimaryContainer = TextPrimary,
    
    // Secondary colors - teal accent
    secondary = GradientAccent,
    onSecondary = BackgroundPrimary,
    secondaryContainer = GradientAccent.copy(alpha = 0.3f),
    onSecondaryContainer = TextPrimary,
    
    // Tertiary colors
    tertiary = GradientEnd,
    onTertiary = BackgroundPrimary,
    tertiaryContainer = GradientEnd.copy(alpha = 0.3f),
    onTertiaryContainer = TextPrimary,
    
    // Error colors
    error = StatusOffline,
    onError = TextPrimary,
    errorContainer = StatusOffline.copy(alpha = 0.3f),
    onErrorContainer = TextPrimary,
    
    // Background colors - deep blue gradient
    background = BackgroundPrimary,
    onBackground = TextPrimary,
    
    // Surface colors - card and panel backgrounds
    surface = BackgroundSecondary,
    onSurface = TextPrimary,
    surfaceVariant = BackgroundTertiary,
    onSurfaceVariant = TextSecondary,
    
    // Outline colors
    outline = CardBorder,
    outlineVariant = CardBorder.copy(alpha = 0.5f),
    
    // Scrim
    scrim = Color(0xFF000000)
)

private val NeuralMindLightColors = lightColorScheme(
    // Keep light theme for compatibility
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = Primary.copy(alpha = 0.1f),
    onPrimaryContainer = PrimaryVariant,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = Secondary.copy(alpha = 0.1f),
    onSecondaryContainer = Secondary,
    tertiary = GradientEnd,
    onTertiary = OnPrimary,
    tertiaryContainer = GradientEnd.copy(alpha = 0.1f),
    onTertiaryContainer = GradientEnd,
    error = Error,
    onError = OnError,
    errorContainer = Error.copy(alpha = 0.1f),
    onErrorContainer = Error,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = Surface.copy(alpha = 0.7f),
    onSurfaceVariant = OnSurface.copy(alpha = 0.7f),
    outline = CardBorder,
    outlineVariant = CardBorder.copy(alpha = 0.5f),
    scrim = Color(0xFF000000)
)

@Composable
fun NeuralMindTheme(
    darkTheme: Boolean = true, // Default to dark theme
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        NeuralMindDarkColors
    } else {
        NeuralMindLightColors
    }
    
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
