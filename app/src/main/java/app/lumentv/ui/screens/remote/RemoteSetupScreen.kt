package app.lumentv.ui.screens.remote

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import app.lumentv.ui.components.Gap
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.PillButton
import app.lumentv.ui.components.TopBar
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.navigation.Routes
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Abre el servidor de importación local y muestra un QR para configurar
 * listas IPTV y servidores VPN desde el móvil.
 */
@Composable
fun RemoteSetupScreen(nav: Navigator) {
    val graph = LocalGraph.current
    val info by graph.importServer.info.collectAsState()
    val event by graph.importServer.lastEvent.collectAsState()
    val back = remember { FocusRequester() }
    val c = Lumen.colors

    DisposableEffect(Unit) {
        graph.importServer.start(graph.importHandler)
        onDispose { graph.importServer.stop() }
    }
    LaunchedEffect(Unit) { runCatching { back.requestFocus() } }

    Column(
        Modifier.fillMaxSize().background(c.background)
            .padding(horizontal = LumenDimens.safeHorizontal, vertical = LumenDimens.safeVertical),
    ) {
        TopBar(nav, Routes.REMOTE_SETUP, title = "Configurar desde el móvil")
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Box(Modifier.clip(RoundedCornerShape(24.dp)).background(Color.White).padding(18.dp)) {
                val url = info?.url
                val qr = remember(url) { url?.let { qrBitmap(it, 600) } }
                if (qr != null) Image(qr, contentDescription = "Código QR", modifier = Modifier.size(260.dp))
                else Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) { LText("Iniciando…", color = Color.Black) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LText("Escanea el código con el móvil", style = Lumen.type.largeTitle)
                LText("El móvil tiene que estar en la misma Wi‑Fi que el Fire TV. Desde esa página puedes:", style = Lumen.type.body, color = c.textSecondary)
                LText("•  Subir los ficheros .conf de Proton VPN (uno por servidor).", style = Lumen.type.body)
                LText("•  Añadir tu lista IPTV (Xtream Codes o M3U) sin teclear con el mando.", style = Lumen.type.body)
                Gap(height = 6.dp)
                LText("O abre en el navegador:", style = Lumen.type.callout, color = c.textSecondary)
                LText(info?.url ?: "…", style = Lumen.type.title, color = c.accent)
                event?.let {
                    Gap(height = 8.dp)
                    LText(it, style = Lumen.type.headline, color = if (it.startsWith("Error")) c.danger else c.success)
                }
                Gap(height = 12.dp)
                PillButton("Hecho", onClick = { nav.back() }, icon = Icons.AutoMirrored.Rounded.ArrowBack, primary = true, modifier = Modifier.focusRequester(back))
                LText("El acceso se cierra al salir de esta pantalla.", style = Lumen.type.caption, color = c.textTertiary)
            }
        }
    }
}

private fun qrBitmap(text: String, size: Int): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}
