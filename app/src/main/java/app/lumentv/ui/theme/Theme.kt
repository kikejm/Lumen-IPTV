package app.lumentv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import app.lumentv.R

/**
 * Tokens de color de Lumen (ver DESIGN.md). Inspirados en los colores de sistema de Apple:
 * fondo negro puro, superficies "glass" y un único acento azul. Solo hay tema oscuro.
 */
@Immutable
data class LumenColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceFocused: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val hairline: Color,
    val focusRing: Color,
    val scrim: Color,
)

val DarkLumenColors = LumenColors(
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surfaceElevated = Color(0xFF2C2C2E),
    surfaceFocused = Color(0xFF3A3A3C),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0x99EBEBF5),
    textTertiary = Color(0x4DEBEBF5),
    accent = Color(0xFF0A84FF),
    onAccent = Color(0xFFFFFFFF),
    success = Color(0xFF30D158),
    warning = Color(0xFFFF9F0A),
    danger = Color(0xFFFF453A),
    hairline = Color(0x14FFFFFF),
    focusRing = Color(0xFFFFFFFF),
    scrim = Color(0xCC000000),
)

/** Alan Sans (Google Fonts, OFL) en dos pesos fijos: 800 para títulos y 400 para el resto. */
val AlanSans = FontFamily(
    Font(R.font.alan_sans_regular, FontWeight.Normal),
    Font(R.font.alan_sans_extrabold, FontWeight.ExtraBold),
)

/** Escala tipográfica en sp para 1080p a ~3 m (los dp de TV ya escalan con la densidad). */
@Immutable
data class LumenType(
    val largeTitle: TextStyle = TextStyle(fontFamily = AlanSans, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, letterSpacing = (-0.02).em),
    val title: TextStyle = TextStyle(fontFamily = AlanSans, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = (-0.01).em),
    val headline: TextStyle = TextStyle(fontFamily = AlanSans, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp),
    val body: TextStyle = TextStyle(fontFamily = AlanSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    val callout: TextStyle = TextStyle(fontFamily = AlanSans, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    val caption: TextStyle = TextStyle(fontFamily = AlanSans, fontWeight = FontWeight.Normal, fontSize = 11.sp, letterSpacing = 0.04.em),
)

object LumenDimens {
    val safeHorizontal = 48.dp
    val safeVertical = 27.dp
    val radiusLarge = 20.dp
    val radiusMedium = 14.dp
    val radiusSmall = 10.dp
    const val focusScale = 1.06f
}

val LocalLumenColors = staticCompositionLocalOf { DarkLumenColors }
val LocalLumenType = staticCompositionLocalOf { LumenType() }

object Lumen {
    val colors: LumenColors @Composable get() = LocalLumenColors.current
    val type: LumenType @Composable get() = LocalLumenType.current
}

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
