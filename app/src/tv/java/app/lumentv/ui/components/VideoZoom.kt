package app.lumentv.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/** Animación de ampliar/reducir un vídeo. `Animatable.animateTo` parte del valor actual: se puede invertir a medias. */
val VideoZoomSpec = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)

/**
 * Coloca el elemento en `bounds` (px, relativos al padre, que debe ocupar lo mismo que la zona de referencia).
 * Se lee en la fase de layout: un zoom no recompone la pantalla en cada fotograma.
 *
 * Es layout real, no `graphicsLayer`: el `SurfaceView` del vídeo vive fuera de Compose y no sigue las
 * transformaciones de una capa, así que hay que mover y redimensionar su vista. Por eso el mismo vídeo debe
 * seguir compuesto durante todo el zoom: cambiar de `SurfaceView` deja un instante en negro.
 */
fun Modifier.placedAt(bounds: () -> Rect): Modifier = layout { measurable, constraints ->
    val r = bounds()
    val placeable = measurable.measure(
        Constraints.fixed(r.width.roundToInt().coerceAtLeast(0), r.height.roundToInt().coerceAtLeast(0)),
    )
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(r.left.roundToInt(), r.top.roundToInt()) }
}
