package app.lumentv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens

/**
 * Campo de texto del móvil: etiqueta en mayúsculas encima y caja `surface-elevated`, como en el
 * diseño de Stitch (09-alta-lista). Con `password`, ojo para mostrar u ocultar.
 */
@Composable
fun LumenTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    val c = Lumen.colors
    var focused by remember { mutableStateOf(false) }
    var reveal by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(LumenDimens.radiusSmall)
    Column(modifier) {
        LText(label.uppercase(), style = Lumen.type.caption, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = Lumen.type.body.copy(color = c.textPrimary),
            cursorBrush = SolidColor(c.accent),
            visualTransformation = if (password && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else keyboardType,
                imeAction = imeAction,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }, onGo = { onDone() }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(shape)
                        .background(c.surfaceElevated)
                        .border(1.5.dp, if (focused) c.accent else Color.Transparent, shape)
                        .padding(start = 14.dp, end = if (password) 4.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
                        if (value.isEmpty()) LText(placeholder, style = Lumen.type.body, color = c.textTertiary, maxLines = 1)
                        inner()
                    }
                    if (password) {
                        TapSurface(onClick = { reveal = !reveal }, modifier = Modifier.size(44.dp), radius = 22.dp, color = Color.Transparent) {
                            Icon(
                                if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (reveal) "Ocultar contraseña" else "Mostrar contraseña",
                                tint = c.textSecondary,
                                modifier = Modifier.align(Alignment.Center).size(20.dp),
                            )
                        }
                    }
                }
            },
        )
    }
}
