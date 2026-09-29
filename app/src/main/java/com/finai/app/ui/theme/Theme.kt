package com.finai.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

private val FinaiLightColorScheme = lightColorScheme(
    primary = Color(0xFF10B981),
    onPrimary = Color.White,
    secondary = Color(0xFF18181B),
    onSecondary = Color.White,
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF18181B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF18181B),
    surfaceVariant = Color(0xFFF4F4F5),
    onSurfaceVariant = Color(0xFF52525B),
    error = Color(0xFFBE123C),
    onError = Color.White,
    outline = Color(0xFFE4E4E7),
)

/**
 * Os containers do Material 3 (diálogo, menu, calendário, bottom sheet) são preenchidos um a
 * um: os padrões do `darkColorScheme` são arroxeados e destoariam do cinza do app.
 */
private val FinaiDarkColorScheme = darkColorScheme(
    primary = Color(0xFF10B981),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0F2A22),
    onPrimaryContainer = Color(0xFF6EE7B7),
    secondary = Color(0xFFE4E4E7),
    onSecondary = Color(0xFF18181B),
    secondaryContainer = Color(0xFF2A2A30),
    onSecondaryContainer = Color(0xFFF4F4F5),
    tertiary = Color(0xFF34D399),
    onTertiary = Color(0xFF0E0E10),
    background = Color(0xFF0E0E10),
    onBackground = Color(0xFFF4F4F5),
    surface = Color(0xFF18181B),
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF26262A),
    onSurfaceVariant = Color(0xFFC4C4CC),
    surfaceTint = Color(0xFF18181B),
    inverseSurface = Color(0xFFF4F4F5),
    inverseOnSurface = Color(0xFF18181B),
    inversePrimary = Color(0xFF047857),
    error = Color(0xFFFB7185),
    onError = Color(0xFF18181B),
    errorContainer = Color(0xFF3A1A20),
    onErrorContainer = Color(0xFFFDA4AF),
    outline = Color(0xFF3F3F46),
    outlineVariant = Color(0xFF34343A),
    scrim = Color.Black,
    surfaceBright = Color(0xFF2A2A30),
    surfaceDim = Color(0xFF0E0E10),
    surfaceContainerLowest = Color(0xFF0E0E10),
    surfaceContainerLow = Color(0xFF151518),
    surfaceContainer = Color(0xFF1C1C20),
    surfaceContainerHigh = Color(0xFF222226),
    surfaceContainerHighest = Color(0xFF2A2A30),
)

/**
 * Root theme for the whole app. All screens should be wrapped in this once,
 * at the app root (see MainActivity) — never re-apply per screen.
 *
 * [darkTheme] vem da escolha em Configurações → Aparência ([com.finai.app.data.prefs.ThemeMode]).
 * A troca é gravada em [FinaiColors.isDark] aqui, antes de qualquer tela ler um token, para o
 * mesmo quadro já sair com as cores novas; as telas fora desta passada são invalidadas pelo
 * próprio estado.
 */
@Composable
fun FinaiTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    if (FinaiColors.isDark != darkTheme) FinaiColors.isDark = darkTheme
    val scheme: ColorScheme = remember(darkTheme) { if (darkTheme) FinaiDarkColorScheme else FinaiLightColorScheme }
    MaterialTheme(
        colorScheme = scheme,
        typography = FinaiTypography,
        shapes = FinaiShapes,
        content = content,
    )
}
