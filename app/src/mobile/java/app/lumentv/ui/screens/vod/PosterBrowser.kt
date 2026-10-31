package app.lumentv.ui.screens.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.lumentv.ui.adaptive.LocalWidthClass
import app.lumentv.ui.adaptive.WidthClass
import app.lumentv.ui.components.Artwork
import app.lumentv.ui.components.Chip
import app.lumentv.ui.components.EmptyState
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.PillButton
import app.lumentv.ui.components.TapSurface
import app.lumentv.ui.components.TopBar
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.screens.live.CategoryItem
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens
import coil.compose.AsyncImage

data class PosterItem(
    val id: String,
    val title: String,
    val image: String,
    val backdrop: String = "",
    val meta: String = "",
    val plot: String = "",
    val rating: String = "",
)

/**
 * Navegador de pósters de Cine y Series (Stitch 04-cine / L04-cine / T04-cine): *hero* con el
 * primer título de la categoría, chips de categoría y rejilla 2:3. Todo se desplaza junto. Sin
 * foco en el móvil, el *hero* muestra el destacado de la categoría elegida. Pulsación larga en un
 * póster = opciones (Mi lista…); el botón «Mi lista» del *hero* hace lo mismo sin pulsar largo.
 */
@Composable
fun PosterBrowser(
    nav: Navigator,
    insets: PaddingValues,
    title: String,
    categories: List<CategoryItem>,
    selectedCategory: String,
    onSelectCategory: (CategoryItem) -> Unit,
    onCategoryMenu: (CategoryItem) -> Unit,
    items: List<PosterItem>,
    onOpen: (PosterItem) -> Unit,
    onItemMenu: (PosterItem) -> Unit,
    onToggleFavorite: (PosterItem) -> Unit,
    favorites: Set<String>,
    emptyTitle: String,
    emptyMessage: String,
) {
    val c = Lumen.colors
    val compact = LocalWidthClass.current == WidthClass.Compact
    val gridState = rememberLazyGridState()
    LaunchedEffect(selectedCategory) { gridState.scrollToItem(0) }
    val hero = items.firstOrNull()

    Column(Modifier.fillMaxSize().padding(insets)) {
        Box(Modifier.padding(horizontal = 16.dp)) { TopBar(nav, title) }
        LazyVerticalGrid(
            // 100 dp de mínimo: 3 columnas en un móvil en vertical, 6 en horizontal y 8–10 en tableta.
            columns = GridCells.Adaptive(100.dp),
            state = gridState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (hero != null) {
                item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
                    Hero(hero, isFavorite = hero.id in favorites, wide = !compact, onOpen = { onOpen(hero) }, onToggleFavorite = { onToggleFavorite(hero) })
                }
            }
            item(key = "categories", span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories, key = { it.id }) { cat ->
                        val on = cat.id == selectedCategory
                        TapSurface(
                            onClick = { onSelectCategory(cat) },
                            onLongClick = { onCategoryMenu(cat) },
                            radius = 50.dp,
                            color = if (on) c.accent else c.surface,
                            modifier = Modifier.heightIn(min = 40.dp),
                        ) {
                            Row(Modifier.align(Alignment.Center).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (cat.locked) Icon(Icons.Rounded.Lock, contentDescription = "Bloqueada", tint = if (on) c.onAccent else c.textSecondary, modifier = Modifier.size(14.dp))
                                LText(cat.name, style = Lumen.type.callout, color = if (on) c.onAccent else c.textPrimary, maxLines = 1)
                                cat.count?.let { LText("$it", style = Lumen.type.caption, color = if (on) c.onAccent.copy(alpha = 0.7f) else c.textTertiary) }
                            }
                        }
                    }
                }
            }
            if (items.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { EmptyState(Icons.Rounded.Movie, emptyTitle, emptyMessage) }
            }
            items(items, key = { it.id }) { item ->
                Column {
                    TapSurface(
                        onClick = { onOpen(item) },
                        onLongClick = { onItemMenu(item) },
                        modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                        radius = LumenDimens.radiusMedium,
                        color = c.surfaceElevated,
                    ) {
                        Artwork(item.image, item.title, Modifier.fillMaxSize(), radius = LumenDimens.radiusMedium, contentScale = ContentScale.Crop)
                        if (item.id in favorites) Chip("Mi lista", c.accent, filled = true, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                    }
                    LText(item.title, style = Lumen.type.callout, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                    if (item.rating.isNotBlank() && item.rating != "0") LText("★ ${item.rating}", style = Lumen.type.caption, color = c.textTertiary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Hero(item: PosterItem, isFavorite: Boolean, wide: Boolean, onOpen: () -> Unit, onToggleFavorite: () -> Unit) {
    val c = Lumen.colors
    val art = item.backdrop.ifBlank { item.image }
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (wide) 220.dp else 260.dp) // en horizontal, más bajo para dejar ver la rejilla
            .clip(RoundedCornerShape(LumenDimens.radiusLarge))
            .background(c.surface),
    ) {
        if (art.isNotBlank()) {
            AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        // Overlay sobre imagen: degradado a negro para que el texto se lea.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.2f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.92f))))
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip("Destacado", c.accent, filled = true)
            LText(item.title, style = Lumen.type.title, color = Color.White, maxLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (item.rating.isNotBlank() && item.rating != "0") LText("★ ${item.rating}", style = Lumen.type.callout, color = c.warning)
                if (item.meta.isNotBlank()) LText(item.meta, style = Lumen.type.callout, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Ver", onClick = onOpen, primary = true, icon = Icons.Rounded.PlayArrow)
                PillButton(if (isFavorite) "En Mi lista" else "Mi lista", onClick = onToggleFavorite, icon = if (isFavorite) Icons.Rounded.Check else Icons.Rounded.Add)
            }
        }
    }
}
