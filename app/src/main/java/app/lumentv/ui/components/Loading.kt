package app.lumentv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import app.lumentv.ui.theme.Lumen
import kotlinx.coroutines.delay

/** Anillo que gira sin parar: pista tenue y un arco de un cuarto de vuelta. */
@Composable
fun LoadingSpinner(modifier: Modifier = Modifier, size: Dp = 48.dp, stroke: Dp = 4.dp, color: Color = Color.White) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_000, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    Canvas(modifier.size(size)) {
        val px = stroke.toPx()
        val inset = px / 2
        val arcSize = androidx.compose.ui.geometry.Size(this.size.width - px, this.size.height - px)
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        drawArc(color.copy(alpha = 0.2f), 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(px))
        rotate(angle) {
            drawArc(color, -90f, 100f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(px, cap = StrokeCap.Round))
        }
    }
}

/**
 * «Cargando» con los puntos apareciendo en bucle (. .. ...). Los puntos que aún no tocan se
 * pintan transparentes para que el texto no cambie de ancho ni se mueva.
 */
@Composable
fun LoadingText(text: String = "Cargando", style: TextStyle = Lumen.type.headline, color: Color = Color.White) {
    var dots by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            dots = (dots + 1) % 4
        }
    }
    Text(
        buildAnnotatedString {
            append(text)
            withStyle(SpanStyle(color = color)) { append(".".repeat(dots)) }
            withStyle(SpanStyle(color = Color.Transparent)) { append(".".repeat(3 - dots)) }
        },
        style = style,
        color = color,
    )
}

/** Indicador del reproductor a pantalla completa: «Cargando…» animado dentro del anillo que gira. */
@Composable
fun PlayerLoading(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        LoadingSpinner(size = 150.dp, stroke = 4.dp)
        LoadingText()
    }
}
