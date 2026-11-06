package app.lumentv.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Clase de ancho de ventana (DESIGN-MOBILE.md → Layout). Mismos cortes que el `WindowSizeClass`
 * de Material: < 600 dp, 600–840 dp y > 840 dp. Se calcula con el ancho de la ventana, no con la
 * orientación, así que también vale para plegables, tabletas y multiventana.
 */
enum class WidthClass {
    /** Móvil en vertical: barra inferior y una columna. */
    Compact,

    /** Móvil en horizontal o plegable: raíl lateral y dos paneles. */
    Medium,

    /** Tableta en horizontal: raíl lateral y dos o tres paneles. */
    Expanded,
}

@Composable
fun currentWidthClass(): WidthClass {
    val width = LocalConfiguration.current.screenWidthDp
    return when {
        width < 600 -> WidthClass.Compact
        width < 840 -> WidthClass.Medium
        else -> WidthClass.Expanded
    }
}

/** La fija `MobileRoot` para que cualquier pantalla sepa cómo maquetarse sin recalcularlo. */
val LocalWidthClass = staticCompositionLocalOf { WidthClass.Compact }

/**
 * Altura compacta (< 480 dp, el corte de Material): el móvil en horizontal. Ahí no caben una
 * encima de otra la barra superior, la previsualización y la lista, así que TV en directo se
 * maqueta aparte, y el raíl lateral prescinde de su cabecera para que quepan las cinco pestañas.
 */
@Composable
fun isCompactHeight(): Boolean = LocalConfiguration.current.screenHeightDp < 480
