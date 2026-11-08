package app.lumentv.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
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

    /** Móvil en horizontal o plegable: raíl lateral (en el móvil, pestañas arriba) y dos paneles. */
    Medium,

    /** Tableta en horizontal: raíl lateral (en el móvil en horizontal, pestañas arriba) y dos o tres paneles. */
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
 * maqueta aparte, y la navegación principal va en una barra de pestañas arriba ([LocalTopNav]).
 */
@Composable
fun isCompactHeight(): Boolean = LocalConfiguration.current.screenHeightDp < 480

/**
 * La fija `MobileRoot`: true en las pestañas principales con el móvil en horizontal, donde la barra
 * de pestañas de arriba ya lleva la marca, la VPN, buscar y ajustes. Ahí `TopBar` no se pinta (salvo
 * con flecha atrás), para no repetir la barra y no gastar alto, que es lo que falta en horizontal.
 */
val LocalTopNav = compositionLocalOf { false }
