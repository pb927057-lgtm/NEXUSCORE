package com.vel.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = VelPrimaryCyan,
    onPrimary = VelBgMain,
    primaryContainer = VelSurfaceElevated,
    onPrimaryContainer = VelPrimaryCyan,
    secondary = VelTextSecondary,
    onSecondary = VelTextPrimary,
    background = VelBgMain,
    onBackground = VelTextPrimary,
    surface = VelSurface,
    onSurface = VelTextPrimary,
    surfaceVariant = VelSurfaceElevated,
    onSurfaceVariant = VelTextSecondary,
    outline = VelBorder,
    error = VelError,
    onError = VelTextPrimary
)

@Composable
fun VelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
