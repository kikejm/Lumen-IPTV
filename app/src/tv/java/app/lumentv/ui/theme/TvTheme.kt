package app.lumentv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** Tema de la UI de TV: tokens de Lumen sobre el `MaterialTheme` de Compose for TV. */
@Composable
fun LumenTheme(content: @Composable () -> Unit) {
    val colors = DarkLumenColors
    val scheme = darkColorScheme(
        primary = colors.accent, onPrimary = colors.onAccent,
        background = colors.background, onBackground = colors.textPrimary,
        surface = colors.surface, onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceElevated, onSurfaceVariant = colors.textSecondary,
        border = colors.focusRing, error = colors.danger,
    )
    CompositionLocalProvider(LocalLumenColors provides colors, LocalLumenType provides LumenType()) {
        MaterialTheme(colorScheme = scheme) {
            // Fuera de una Surface el color de contenido por defecto sería negro: lo fijamos al primario.
            CompositionLocalProvider(LocalContentColor provides colors.textPrimary, content = content)
        }
    }
}
