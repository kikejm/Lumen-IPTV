package app.lumentv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens
import coil.compose.AsyncImage

/**
 * Superficie pulsable: equivalente táctil de `FocusSurface` de la TV. Sin escala ni anillo;
 * responde con un *ripple* sutil. `onLongClick` es el ☰ de la TV, y por regla (DESIGN-MOBILE.md)
 * la misma acción tiene que estar también en un botón visible («⋯»).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TapSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    radius: Dp = LumenDimens.radiusMedium,
    color: Color = Lumen.colors.surface,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .clip(shape)
            .background(color)
            .combinedClickable(
                enabled = enabled,
                interactionSource = null,
                indication = ripple(color = Lumen.colors.textPrimary),
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        content = content,
    )
}

/** Texto con elipsis por defecto; el color por omisión es el del contenido. */
@Composable
fun LText(
    text: String,
    style: TextStyle = Lumen.type.body,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = modifier,
    )
}

/** Botón píldora. `primary` = blanco con texto negro (el «Reproducir» de Apple TV). Alto mínimo táctil 48 dp. */
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
    val bg = if (primary) Color.White else c.surfaceElevated
    val fg = if (primary) Color.Black else c.textPrimary
    TapSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        radius = 50.dp,
        color = bg.copy(alpha = if (enabled) 1f else 0.4f),
    ) {
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            LText(text, style = Lumen.type.callout.copy(fontWeight = Lumen.type.headline.fontWeight), color = fg, maxLines = 1)
        }
    }
}

/** Botón redondo solo con icono (buscar, ajustes, atrás…). Área táctil de 48 dp. */
@Composable
fun IconCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Lumen.colors.surface,
) {
    TapSurface(onClick = onClick, modifier = modifier.size(48.dp), radius = 24.dp, color = color) {
        Icon(icon, contentDescription = contentDescription, tint = Lumen.colors.textPrimary, modifier = Modifier.align(Alignment.Center).size(22.dp))
    }
}

@Composable
fun StatusDot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** Etiqueta en mayúsculas sobre color al 16 % (o sólido). */
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

/** Filtro pulsable (categorías, calidad, Xtream/M3U…): el activo en acento. */
@Composable
fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Lumen.colors
    TapSurface(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 40.dp),
        radius = 50.dp,
        color = if (selected) c.accent else c.surface,
    ) {
        LText(
            text,
            style = Lumen.type.callout,
            color = if (selected) c.onAccent else c.textPrimary,
            maxLines = 1,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp, vertical = 9.dp),
        )
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

/** Logo de canal o póster; las iniciales solo aparecen si no hay imagen o falla al cargar. */
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
        var failed by remember(url) { mutableStateOf(url.isBlank()) }
        if (failed) {
            val initials = fallback.split(' ', ':').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase()
            LText(initials, style = Lumen.type.headline, color = Lumen.colors.textSecondary)
        }
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = fallback,
                contentScale = contentScale,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Título de sección en mayúsculas (caption), con acción opcional a la derecha («Ver todo»). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        LText(text.uppercase(), style = Lumen.type.caption, color = Lumen.colors.textSecondary, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Estado vacío o de error: icono, título, texto que dice qué hacer y acciones. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    iconTint: Color = Lumen.colors.textSecondary,
    actions: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(36.dp)) }
        Spacer(Modifier.height(20.dp))
        LText(title, style = Lumen.type.title, color = Lumen.colors.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        LText(message, style = Lumen.type.body, color = Lumen.colors.textSecondary, textAlign = TextAlign.Center)
        if (actions != null) {
            Spacer(Modifier.height(24.dp))
            actions()
        }
    }
}

@Composable
fun Gap(width: Dp = 0.dp, height: Dp = 0.dp) {
    Spacer(Modifier.width(width).height(height))
}
