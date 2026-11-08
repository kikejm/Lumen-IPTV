package app.lumentv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.lumentv.ui.adaptive.LocalWidthClass
import app.lumentv.ui.adaptive.WidthClass
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens

/**
 * Contenedor de opciones adaptable (DESIGN-MOBILE.md): hoja inferior en vertical (*Compact*)
 * y hoja lateral derecha de 360 dp en horizontal y tableta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = Lumen.colors
    if (LocalWidthClass.current == WidthClass.Compact) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(topStart = LumenDimens.radiusLarge, topEnd = LumenDimens.radiusLarge),
            containerColor = c.surface,
            contentColor = c.textPrimary,
            scrimColor = c.scrim,
            dragHandle = { BottomSheetDefaults.DragHandle(color = c.textTertiary) },
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding(),
                content = content,
            )
        }
    } else {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null, onClick = onDismiss)) {
                Column(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(360.dp)
                        .clip(RoundedCornerShape(topStart = LumenDimens.radiusLarge, bottomStart = LumenDimens.radiusLarge))
                        .background(c.surface)
                        // Los toques dentro del panel no deben cerrarlo.
                        .clickable(interactionSource = null, indication = null, onClick = {})
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    content = content,
                )
            }
        }
    }
}

/** Una opción del menú contextual (el ☰ de la TV). */
data class SheetOption(
    val label: String,
    val icon: ImageVector? = null,
    val detail: String? = null,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Menú contextual: se abre con pulsación larga o con el botón «⋯». Cada opción cierra la hoja
 * antes de ejecutar su acción. Siempre termina con «Cancelar».
 */
@Composable
fun OptionsSheet(
    title: String,
    options: List<SheetOption>,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    header: (@Composable () -> Unit)? = null,
) {
    val c = Lumen.colors
    AdaptiveSheet(onDismiss) {
        if (header != null) header() else {
            LText(title, style = Lumen.type.headline, maxLines = 2)
            if (subtitle != null) LText(subtitle, style = Lumen.type.callout, color = c.textSecondary, maxLines = 2)
        }
        Gap(height = 12.dp)
        options.forEach { o ->
            TapSurface(
                onClick = { onDismiss(); o.onClick() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                radius = LumenDimens.radiusSmall,
                color = c.surface,
            ) {
                Row(
                    Modifier.align(Alignment.CenterStart).padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val fg = if (o.destructive) c.danger else c.textPrimary
                    if (o.icon != null) Icon(o.icon, contentDescription = null, tint = if (o.destructive) c.danger else c.accent, modifier = Modifier.size(22.dp))
                    LText(o.label, style = Lumen.type.body, color = fg, maxLines = 1, modifier = Modifier.weight(1f))
                    if (o.detail != null) LText(o.detail, style = Lumen.type.callout, color = c.textTertiary, maxLines = 1)
                }
            }
        }
        Gap(height = 8.dp)
        PillButton("Cancelar", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * PIN parental con teclado numérico propio (no el del sistema), igual que `PinDialog` de la TV.
 * [onSubmit] devuelve `true` si el PIN es correcto; si no, se vacía y se muestra el error.
 */
@Composable
fun PinSheet(
    title: String,
    message: String,
    onSubmit: (String) -> Boolean,
    onDismiss: () -> Unit,
    length: Int = 4,
) {
    val c = Lumen.colors
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    LaunchedEffect(pin) {
        if (pin.length == length) {
            if (onSubmit(pin)) onDismiss() else { wrong = true; pin = "" }
        }
    }
    AdaptiveSheet(onDismiss) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            LText(title, style = Lumen.type.title)
            Gap(height = 6.dp)
            LText(
                if (wrong) "PIN incorrecto. Inténtalo de nuevo." else message,
                style = Lumen.type.callout,
                color = if (wrong) c.danger else c.textSecondary,
            )
            Gap(height = 20.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(length) { i ->
                    Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < pin.length) c.textPrimary else c.surfaceElevated))
                }
            }
            Gap(height = 24.dp)
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    row.forEach { k ->
                        if (k.isEmpty()) {
                            Box(Modifier.size(68.dp))
                        } else {
                            TapSurface(
                                onClick = {
                                    wrong = false
                                    pin = if (k == "⌫") pin.dropLast(1) else if (pin.length < length) pin + k else pin
                                },
                                modifier = Modifier.size(68.dp),
                                radius = 34.dp,
                                color = c.surfaceElevated,
                            ) {
                                if (k == "⌫") {
                                    Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "Borrar", tint = c.textPrimary, modifier = Modifier.align(Alignment.Center))
                                } else {
                                    LText(k, style = Lumen.type.title, modifier = Modifier.align(Alignment.Center))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Confirmación de acciones destructivas o importantes. */
@Composable
fun ConfirmDialog(
    title: String,
    /** Sin texto (`null`) cuando el título ya lo dice todo, como «¿Estás seguro de que quieres salir?». */
    message: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    val c = Lumen.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        titleContentColor = c.textPrimary,
        textContentColor = c.textSecondary,
        title = { LText(title, style = Lumen.type.headline) },
        text = if (message != null) { { LText(message, style = Lumen.type.body) } } else null,
        confirmButton = {
            TapSurface(
                onClick = { onDismiss(); onConfirm() },
                radius = 50.dp,
                color = if (destructive) c.danger else c.textPrimary,
                modifier = Modifier.heightIn(min = 44.dp),
            ) {
                LText(
                    confirmLabel,
                    style = Lumen.type.callout,
                    color = if (destructive) c.textPrimary else c.background,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        },
        dismissButton = {
            TapSurface(onClick = onDismiss, radius = 50.dp, color = c.surfaceElevated, modifier = Modifier.heightIn(min = 44.dp)) {
                LText("Cancelar", style = Lumen.type.callout, modifier = Modifier.align(Alignment.Center).padding(horizontal = 18.dp, vertical = 10.dp))
            }
        },
    )
}
