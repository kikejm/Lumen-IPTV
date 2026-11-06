package app.lumentv.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumentv.tools.SpeedResult
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.PillButton
import app.lumentv.ui.components.TopBar
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens
import app.lumentv.vpn.VpnState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** Ruta propia del móvil (en la TV el test vive dentro de Ajustes). */
const val SPEED_TEST_ROUTE = "speedtest"

/** El test descarga durante ~8 s (`SpeedTest`): el arco avanza con el tiempo, no con la velocidad. */
private const val TEST_MILLIS = 8_000L

/**
 * Test de velocidad (Stitch 15-test-velocidad): descarga real a través de la misma conexión que el
 * vídeo (con la VPN si está activa en «Solo esta app»), latencia y recomendación de calidad.
 */
@Composable
fun SpeedTestScreen(nav: Navigator, insets: PaddingValues) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val active by graph.playlists.active.collectAsState(initial = null)
    val vpn by graph.vpn.state.collectAsState()
    var running by remember { mutableStateOf(false) }
    var live by remember { mutableDoubleStateOf(0.0) }
    var timeFraction by remember { mutableFloatStateOf(0f) }
    var result by remember { mutableStateOf<SpeedResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val c = Lumen.colors

    fun start() {
        if (running) return
        running = true
        error = null
        live = 0.0
        scope.launch {
            runCatching { graph.speedTest.run(active?.serverUrl) { live = it } }
                .onSuccess { result = it }
                .onFailure { error = it.message ?: it.javaClass.simpleName }
            running = false
        }
    }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val t0 = System.currentTimeMillis()
        while (running) {
            timeFraction = ((System.currentTimeMillis() - t0).toFloat() / TEST_MILLIS).coerceIn(0f, 1f)
            delay(100)
        }
    }
    // Se lanza solo al entrar: es lo que se viene a hacer aquí.
    LaunchedEffect(Unit) { start() }

    val mbps = if (running) live else result?.downloadMbps ?: 0.0
    val arc by animateFloatAsState(
        targetValue = when {
            running -> timeFraction
            result != null -> 1f
            else -> 0f
        },
        animationSpec = tween(200),
        label = "arc",
    )
    val vpnServer = (vpn as? VpnState.Connected)?.server

    Column(Modifier.fillMaxSize().padding(insets)) {
        Box(Modifier.padding(horizontal = 16.dp)) { TopBar(nav, "Test de velocidad", showBack = true, showActions = false) }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(Modifier.widthIn(max = 520.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                    Gauge(arc, Modifier.fillMaxSize())
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LText("BAJADA", style = Lumen.type.caption, color = c.textSecondary)
                        LText(
                            if (mbps > 0 || result != null) String.format(Locale("es", "ES"), "%.1f", mbps) else "—",
                            style = Lumen.type.largeTitle.copy(fontSize = 56.sp),
                        )
                        LText("Mb/s", style = Lumen.type.callout, color = c.accent)
                        LText(
                            when {
                                running -> "Midiendo…"
                                error != null -> "No se pudo medir"
                                result != null -> "Completado"
                                else -> ""
                            },
                            style = Lumen.type.callout,
                            color = if (error != null) c.danger else c.textSecondary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile(Icons.Rounded.Bolt, "Latencia", result?.latencyMs?.let { "$it ms" } ?: "—", c.accent, Modifier.weight(1f))
                    Tile(Icons.Rounded.Dns, "Proveedor", result?.iptvLatencyMs?.let { "$it ms" } ?: "—", c.accent, Modifier.weight(1f))
                    Tile(
                        Icons.Rounded.Shield,
                        if (vpnServer != null) "Vía VPN" else "Conexión",
                        vpnServer?.name ?: "Directa",
                        if (vpnServer != null) c.success else c.textSecondary,
                        Modifier.weight(1f),
                    )
                }

                error?.let {
                    LText("Error: $it", style = Lumen.type.callout, color = c.danger, textAlign = TextAlign.Center)
                }
                result?.takeIf { !running }?.let { r -> Diagnosis(r.downloadMbps, vpnServer != null) }

                PillButton(
                    when {
                        running -> "Midiendo…"
                        result != null || error != null -> "Repetir test"
                        else -> "Iniciar test"
                    },
                    onClick = ::start,
                    icon = if (result != null || error != null) Icons.Rounded.Refresh else Icons.Rounded.Speed,
                    primary = true,
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth(),
                )
                LText(
                    "«Latencia» es el tiempo de respuesta de internet; «Proveedor», el de tu servidor IPTV." +
                        if (vpnServer != null) " La medida incluye el túnel VPN." else "",
                    style = Lumen.type.caption, color = c.textTertiary, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Gauge(fraction: Float, modifier: Modifier) {
    val track = Lumen.colors.surfaceElevated
    val accent = Lumen.colors.accent
    Canvas(modifier.padding(12.dp)) {
        val stroke = 14.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        // Arco de 270° abierto por abajo, como un velocímetro.
        drawArc(track, startAngle = 135f, sweepAngle = 270f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        if (fraction > 0f) {
            drawArc(accent, startAngle = 135f, sweepAngle = 270f * fraction, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun Tile(icon: ImageVector, label: String, value: String, tint: Color, modifier: Modifier) {
    val c = Lumen.colors
    Column(
        modifier.clip(RoundedCornerShape(LumenDimens.radiusMedium)).background(c.surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(c.surfaceElevated), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        LText(label, style = Lumen.type.callout, color = c.textSecondary, maxLines = 1)
        LText(value, style = Lumen.type.headline, maxLines = 1)
    }
}

@Composable
private fun Diagnosis(mbps: Double, viaVpn: Boolean) {
    val c = Lumen.colors
    val (title, detail) = when {
        mbps >= 25 -> "Suficiente para 4K" to "Puedes ver cualquier calidad sin cortes."
        mbps >= 10 -> "Suficiente para Full HD" to "Los canales FHD irán bien; el 4K puede cortarse."
        mbps >= 5 -> "Suficiente para HD" to "Elige canales HD o SD y sube el búfer a 30 s si hay cortes."
        else -> "Conexión justa" to if (viaVpn) "Prueba un servidor VPN más cercano o desactiva la VPN." else "Prueba con wifi o elige canales SD."
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(LumenDimens.radiusLarge)).background(c.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LText("DIAGNÓSTICO", style = Lumen.type.caption, color = c.accent)
        LText(title, style = Lumen.type.title)
        LText(detail, style = Lumen.type.body, color = c.textSecondary)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            QualityDot("HD", mbps >= 5)
            QualityDot("Full HD", mbps >= 10)
            QualityDot("4K", mbps >= 25)
        }
    }
}

@Composable
private fun QualityDot(label: String, ok: Boolean) {
    val c = Lumen.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(if (ok) c.success else c.textTertiary))
        LText(label, style = Lumen.type.callout, color = if (ok) c.textPrimary else c.textTertiary)
    }
}
