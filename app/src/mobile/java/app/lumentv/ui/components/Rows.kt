package app.lumentv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens
import kotlinx.coroutines.delay

/** Interruptor de Material 3 con los colores de Lumen (activo en acento). */
@Composable
fun LumenSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Lumen.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = c.accent,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = c.textSecondary,
            uncheckedTrackColor = c.surfaceElevated,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/**
 * Grupo de filas sobre `surface` (radio 14 dp) separadas por una línea fina, como las listas
 * agrupadas de Ajustes en Stitch (08-ajustes). [title] va encima, en `caption`.
 */
@Composable
fun GroupCard(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) SectionTitle(title, Modifier.padding(start = 4.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(LumenDimens.radiusMedium)).background(Lumen.colors.surface),
            content = content,
        )
    }
}

/** Línea fina entre filas de un [GroupCard]. */
@Composable
fun RowDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(Lumen.colors.hairline))
}

/**
 * Fila de ajuste: icono opcional, título, explicación y, a la derecha, el valor actual o lo que
 * pase [trailing]. Pulsable entera; alto mínimo táctil de 56 dp.
 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = Lumen.colors.accent,
    titleColor: Color = Lumen.colors.textPrimary,
    chevron: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = Lumen.colors
    val content: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                LText(title, style = Lumen.type.body, color = if (enabled) titleColor else c.textTertiary, maxLines = 2)
                if (!subtitle.isNullOrBlank()) LText(subtitle, style = Lumen.type.callout, color = c.textSecondary, maxLines = 3)
            }
            if (!value.isNullOrBlank()) {
                LText(value, style = Lumen.type.callout, color = c.textSecondary, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 160.dp))
            }
            trailing?.invoke(this)
            if (chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.textTertiary)
        }
    }
    if (onClick != null) {
        TapSurface(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth(), radius = 0.dp, color = Color.Transparent) { content() }
    } else {
        Box(modifier.fillMaxWidth()) { content() }
    }
}

/** Fila con interruptor: tocar la fila o el interruptor cambia el valor. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        enabled = enabled,
        modifier = modifier,
        onClick = { onCheckedChange(!checked) },
        trailing = { LumenSwitch(checked, onCheckedChange, enabled = enabled) },
    )
}

/**
 * Hoja con un único campo de texto y «Guardar» (User-Agent, URL de una fuente EPG, URL de un
 * `.conf`…). En horizontal es la hoja lateral, que deja sitio al teclado.
 */
@Composable
fun TextInputSheet(
    title: String,
    initial: String,
    label: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    message: String? = null,
    placeholder: String = "",
    saveLabel: String = "Guardar",
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var value by remember { mutableStateOf(initial) }
    val save = { onSave(value.trim()); onDismiss() }
    AdaptiveSheet(onDismiss) {
        LText(title, style = Lumen.type.headline)
        if (message != null) {
            Gap(height = 6.dp)
            LText(message, style = Lumen.type.callout, color = Lumen.colors.textSecondary)
        }
        Gap(height = 16.dp)
        LumenTextField(value, { value = it }, label, placeholder = placeholder, keyboardType = keyboardType, imeAction = ImeAction.Done, onDone = save)
        Gap(height = 16.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(saveLabel, onClick = save, primary = true, modifier = Modifier.weight(1f))
            PillButton("Cancelar", onClick = onDismiss, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Aviso breve abajo al centro (resultado de una importación, «PIN guardado»…). Desaparece solo a
 * los 4 s; [onShown] lo limpia para que no vuelva a salir al recomponer.
 */
@Composable
fun NoticePill(text: String?, onShown: () -> Unit, modifier: Modifier = Modifier, error: Boolean = false) {
    var visible by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(text) {
        if (text == null) return@LaunchedEffect
        visible = text
        delay(4_000)
        visible = null
        onShown()
    }
    AnimatedVisibility(visible = visible != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        val c = Lumen.colors
        Box(
            Modifier
                .padding(16.dp)
                .clip(RoundedCornerShape(50))
                .background(if (error) c.danger else c.surfaceElevated)
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            LText(visible.orEmpty(), style = Lumen.type.callout, color = c.textPrimary, textAlign = TextAlign.Center, maxLines = 3)
        }
    }
}
