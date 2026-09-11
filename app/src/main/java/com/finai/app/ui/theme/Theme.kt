package com.finai.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val FinaiColorScheme = lightColorScheme(
    primary = FinaiColors.Emerald,
    onPrimary = FinaiColors.TextOnDarkFull,
    secondary = FinaiColors.Ink,
    onSecondary = FinaiColors.TextOnDarkFull,
    background = FinaiColors.Background,
    onBackground = FinaiColors.TextPrimary,
    surface = FinaiColors.Surface,
    onSurface = FinaiColors.TextPrimary,
    surfaceVariant = FinaiColors.SurfaceMuted,
    onSurfaceVariant = FinaiColors.TextSecondary,
    error = FinaiColors.RoseDark,
    onError = FinaiColors.TextOnDarkFull,
    outline = FinaiColors.BorderSubtle,
)

/**
 * Root theme for the whole app. All screens should be wrapped in this once,
 * at the app root (see FinaiApp.kt) — never re-apply per screen.
 */
@Composable
fun FinaiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FinaiColorScheme,
        typography = FinaiTypography,
        shapes = FinaiShapes,
        content = content,
    )
}
