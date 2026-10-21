package app.lumentv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import app.lumentv.ui.theme.Lumen

data class MenuOption(val label: String, val icon: ImageVector? = null, val selected: Boolean = false, val action: () -> Unit)

/** Menú de opciones (botón ☰ del mando): pistas de audio, favoritos, bloquear categoría… */
@Composable
fun OptionsDialog(title: String, options: List<MenuOption>, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        GlassPanel(Modifier.width(420.dp)) {
            Column(Modifier.padding(20.dp)) {
                LText(title, style = Lumen.type.headline, color = Lumen.colors.textPrimary)
                Gap(height = 12.dp)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    items(options.size) { i ->
                        val o = options[i]
                        FocusSurface(
                            onClick = { o.action(); onDismiss() },
                            modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                            containerColor = Color.Transparent,
                            focusedScale = 1.02f,
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (o.icon != null) Icon(o.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                                LText(o.label, style = Lumen.type.body, maxLines = 1, modifier = Modifier.weight(1f))
                                if (o.selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Lumen.colors.accent, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/**
 * Diálogo de PIN de 4 dígitos con teclado numérico navegable por D-pad
 * (el mando del Fire TV no tiene números).
 */
@Composable
fun PinDialog(title: String, subtitle: String = "", onDismiss: () -> Unit, onPin: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
    Dialog(onDismissRequest = onDismiss) {
        GlassPanel(Modifier.width(360.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                LText(title, style = Lumen.type.headline, color = Lumen.colors.textPrimary)
                if (subtitle.isNotEmpty()) LText(subtitle, style = Lumen.type.callout, color = Lumen.colors.textSecondary)
                Gap(height = 16.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(4) { i ->
                        val filled = i < pin.length
                        Box(
                            Modifier.size(14.dp).clip(CircleShape)
                                .background(if (filled) (if (error) Lumen.colors.danger else Lumen.colors.textPrimary) else Color.Transparent)
                                .border(1.5.dp, if (error) Lumen.colors.danger else Lumen.colors.textSecondary, CircleShape),
                        )
                    }
                }
                Gap(height = 18.dp)
                keys.chunked(3).forEachIndexed { row, rowKeys ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 5.dp)) {
                        rowKeys.forEachIndexed { col, k ->
                            if (k.isEmpty()) {
                                Box(Modifier.size(64.dp))
                            } else {
                                FocusSurface(
                                    onClick = {
                                        error = false
                                        if (k == "⌫") pin = pin.dropLast(1)
                                        else if (pin.length < 4) {
                                            pin += k
                                            if (pin.length == 4) {
                                                if (!onPin(pin)) { error = true; pin = "" }
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(64.dp).then(if (row == 0 && col == 0) Modifier.focusRequester(first) else Modifier),
                                    radius = 32.dp,
                                    containerColor = Lumen.colors.surfaceElevated,
                                ) {
                                    Box(Modifier.align(Alignment.Center)) {
                                        if (k == "⌫") Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "Borrar", modifier = Modifier.size(20.dp))
                                        else LText(k, style = Lumen.type.title)
                                    }
                                }
                            }
                        }
                    }
                }
                if (error) {
                    Gap(height = 8.dp)
                    LText("PIN incorrecto", style = Lumen.type.callout, color = Lumen.colors.danger)
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/** Confirmación simple Sí/No. */
@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        GlassPanel(Modifier.width(440.dp)) {
            Column(Modifier.padding(24.dp)) {
                LText(title, style = Lumen.type.headline, color = Lumen.colors.textPrimary)
                Gap(height = 8.dp)
                LText(message, style = Lumen.type.body, color = Lumen.colors.textSecondary)
                Gap(height = 20.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton(confirmLabel, onClick = { onConfirm(); onDismiss() }, primary = true, modifier = Modifier.focusRequester(first))
                    PillButton("Cancelar", onClick = onDismiss)
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
