package app.lumentv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.lumentv.core.util.TimeFormatter
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * Superficie enfocable estilo tvOS: al recibir el foco escala, sube de color y muestra un anillo.
 * Es la base de todos los elementos navegables con el mando.
 */
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    radius: Dp = LumenDimens.radiusMedium,
    containerColor: Color = Lumen.colors.surface,
    focusedContainerColor: Color = Lumen.colors.surfaceFocused,
    contentColor: Color = Lumen.colors.textPrimary,
    focusedContentColor: Color = Lumen.colors.textPrimary,
    focusedScale: Float = LumenDimens.focusScale,
    showRing: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            contentColor = contentColor,
            focusedContainerColor = focusedContainerColor,
            focusedContentColor = focusedContentColor,
            pressedContainerColor = focusedContainerColor,
            pressedContentColor = focusedContentColor,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = if (showRing) Border(BorderStroke(2.dp, Lumen.colors.focusRing), shape = shape) else Border.None,
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = Lumen.colors.focusRing.copy(alpha = 0.18f), elevation = 12.dp),
        ),
        content = content,
    )
}

@Composable
fun LText(
    text: String,
    style: TextStyle = Lumen.type.body,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Botón en forma de píldora. `primary` = fondo blanco/acento como el "Reanudar" de Apple TV. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val c = Lumen.colors
    val base = if (primary) (if (c.isDark) Color.White else c.accent) else c.surfaceElevated
    val fg = if (primary) (if (c.isDark) Color.Black else Color.White) else c.textPrimary
    FocusSurface(
        onClick = { if (enabled) onClick() },
        modifier = modifier,
        radius = 50.dp,
        containerColor = base.copy(alpha = if (enabled) 1f else 0.4f),
        focusedContainerColor = if (primary) base else c.surfaceFocused,
        contentColor = fg,
        focusedContentColor = fg,
        focusedScale = 1.05f,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            LText(text, style = Lumen.type.callout, maxLines = 1)
        }
    }
}

/** Botón circular solo con icono (buscar, ajustes…). */
@Composable
fun IconCircleButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        radius = 20.dp,
        containerColor = Lumen.colors.surface,
        focusedScale = 1.1f,
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.align(Alignment.Center).size(20.dp))
    }
}

@Composable
fun StatusDot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun Chip(text: String, color: Color = Lumen.colors.accent, filled: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (filled) color else color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        LText(text.uppercase(), style = Lumen.type.caption, color = if (filled) Color.White else color, maxLines = 1)
    }
}

@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier, color: Color = Lumen.colors.accent, height: Dp = 3.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height))
            .background(Lumen.colors.textTertiary.copy(alpha = 0.35f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(height))
                .background(color),
        )
    }
}

/** Logo de canal o póster con fallback a las iniciales. */
@Composable
fun Artwork(
    url: String,
    fallback: String,
    modifier: Modifier = Modifier,
    radius: Dp = LumenDimens.radiusSmall,
    contentScale: ContentScale = ContentScale.Fit,
    background: Color = Lumen.colors.surfaceElevated,
) {
    Box(modifier.clip(RoundedCornerShape(radius)).background(background), contentAlignment = Alignment.Center) {
        val initials = fallback.split(' ', ':').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase()
        LText(initials, style = Lumen.type.headline, color = Lumen.colors.textSecondary)
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = fallback,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        LText(text, style = Lumen.type.headline, color = Lumen.colors.textPrimary, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Reloj de la barra superior, actualizado cada 15 s. */
@Composable
fun Clock(use24h: Boolean) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    LText(TimeFormatter(use24h).time(now), style = Lumen.type.title, color = Lumen.colors.textPrimary)
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Lumen.colors.textSecondary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        LText(title, style = Lumen.type.title, color = Lumen.colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        LText(message, style = Lumen.type.body, color = Lumen.colors.textSecondary)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

/** Fila de ayudas de mando al pie de pantalla: "OK Ver · ☰ Opciones". */
@Composable
fun KeyHints(vararg hints: Pair<String, String>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        hints.forEach { (key, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.clip(RoundedCornerShape(5.dp)).border(1.dp, Lumen.colors.textTertiary, RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) { LText(key, style = Lumen.type.caption, color = Lumen.colors.textSecondary) }
                LText(label, style = Lumen.type.caption, color = Lumen.colors.textSecondary)
            }
        }
    }
}

/**
 * Campo de texto para TV. Al pulsar OK se abre el teclado del Fire TV.
 * Se dibuja como una superficie más para que el foco sea coherente con el resto.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    focusRequester: FocusRequester? = null,
    onDone: () -> Unit = {},
) {
    var editing by remember { mutableStateOf(false) }
    var restoreFocus by remember { mutableStateOf(false) }
    val surfaceFocus = focusRequester ?: remember { FocusRequester() }
    val editFocus = remember { FocusRequester() }
    val c = Lumen.colors
    fun finish() {
        editing = false
        restoreFocus = true
        onDone()
    }
    Column(modifier) {
        LText(label, style = Lumen.type.caption, color = c.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        if (!editing) {
            // En reposo es una superficie más: el teclado solo se abre al pulsar OK.
            FocusSurface(
                onClick = { editing = true },
                modifier = Modifier.fillMaxWidth().focusRequester(surfaceFocus),
                radius = LumenDimens.radiusSmall,
                focusedScale = 1.02f,
            ) {
                val shown = if (password) "•".repeat(value.length) else value
                LText(
                    shown.ifEmpty { placeholder },
                    style = Lumen.type.body,
                    color = if (value.isEmpty()) c.textTertiary else c.textPrimary,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
            if (restoreFocus) LaunchedEffect(Unit) {
                runCatching { surfaceFocus.requestFocus() }
                restoreFocus = false
            }
        } else {
            var hadFocus by remember { mutableStateOf(false) }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = Lumen.type.body.copy(color = c.textPrimary),
                cursorBrush = SolidColor(c.accent),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(
                    onDone = { finish() }, onSearch = { finish() }, onGo = { finish() }, onNext = { finish() }, onSend = { finish() },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(editFocus)
                    .onFocusChanged {
                        if (it.isFocused) hadFocus = true else if (hadFocus) { editing = false; restoreFocus = true }
                    }
                    .clip(RoundedCornerShape(LumenDimens.radiusSmall))
                    .background(c.surfaceFocused)
                    .border(2.dp, c.accent, RoundedCornerShape(LumenDimens.radiusSmall))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
            LaunchedEffect(Unit) { runCatching { editFocus.requestFocus() } }
        }
    }
}

/** Panel lateral/flotante translúcido ("glass") para overlays. */
@Composable
fun GlassPanel(modifier: Modifier = Modifier, radius: Dp = LumenDimens.radiusLarge, content: @Composable BoxScope.() -> Unit) {
    val c = Lumen.colors
    Box(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(c.surface.copy(alpha = if (c.isDark) 0.82f else 0.92f))
            .border(1.dp, c.hairline, RoundedCornerShape(radius)),
        content = content,
    )
}

@Composable
fun Gap(width: Dp = 0.dp, height: Dp = 0.dp) {
    Spacer(Modifier.width(width).height(height))
}
