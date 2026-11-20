package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LyrixDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF031714),
    primaryContainer = Color(0xFF083338),
    onPrimaryContainer = ElectricCyan,
    secondary = VioletAura,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF231942),
    onSecondaryContainer = Color(0xFFDDD6FE),
    tertiary = MagentaPulse,
    onTertiary = Color.White,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = ObsidianBorder,
    error = CoralAlert,
    onError = Color(0xFF2A050D)
)

private val LyrixShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LyrixTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LyrixDarkColorScheme,
        typography = AppTypography,
        shapes = LyrixShapes,
        content = content
    )
}
