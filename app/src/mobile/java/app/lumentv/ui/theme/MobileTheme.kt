package app.lumentv.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/** Tema de la UI móvil: tokens de Lumen sobre el `MaterialTheme` de Material 3 (solo oscuro). */
@Composable
fun LumenTheme(content: @Composable () -> Unit) {
    val colors = DarkLumenColors
    val scheme = darkColorScheme(
        primary = colors.accent, onPrimary = colors.onAccent,
        background = colors.background, onBackground = colors.textPrimary,
        surface = colors.background, onSurface = colors.textPrimary,
        surfaceVariant = colors.surface, onSurfaceVariant = colors.textSecondary,
        surfaceContainer = colors.surface, surfaceContainerHigh = colors.surfaceElevated,
        outline = colors.hairline, outlineVariant = colors.hairline,
        error = colors.danger,
    )
    CompositionLocalProvider(LocalLumenColors provides colors, LocalLumenType provides LumenType()) {
        MaterialTheme(colorScheme = scheme) {
            CompositionLocalProvider(LocalContentColor provides colors.textPrimary, content = content)
        }
    }
}
