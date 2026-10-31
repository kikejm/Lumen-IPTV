package app.lumentv.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.sp

/**
 * Escala tipográfica del móvil (DESIGN-MOBILE.md): la de la TV con `large-title` a 30 sp
 * y `body` a 15 sp, porque se lee a 30 cm y no a 3 m.
 */
val MobileType: LumenType = LumenType().let {
    it.copy(
        largeTitle = it.largeTitle.copy(fontSize = 30.sp),
        body = it.body.copy(fontSize = 15.sp, lineHeight = 21.sp),
    )
}

/** Tema de la UI móvil: tokens de Lumen sobre el `MaterialTheme` de Material 3 (solo oscuro). */
@Composable
fun LumenTheme(content: @Composable () -> Unit) {
    val colors = DarkLumenColors
    val scheme = darkColorScheme(
        primary = colors.accent, onPrimary = colors.onAccent,
        secondaryContainer = colors.surfaceElevated, onSecondaryContainer = colors.textPrimary,
        background = colors.background, onBackground = colors.textPrimary,
        surface = colors.background, onSurface = colors.textPrimary,
        surfaceVariant = colors.surface, onSurfaceVariant = colors.textSecondary,
        surfaceContainerLow = colors.surface, surfaceContainer = colors.surface,
        surfaceContainerHigh = colors.surfaceElevated, surfaceContainerHighest = colors.surfaceElevated,
        outline = colors.hairline, outlineVariant = colors.hairline,
        scrim = colors.scrim, error = colors.danger,
    )
    CompositionLocalProvider(LocalLumenColors provides colors, LocalLumenType provides MobileType) {
        MaterialTheme(colorScheme = scheme) {
            CompositionLocalProvider(LocalContentColor provides colors.textPrimary, content = content)
        }
    }
}
