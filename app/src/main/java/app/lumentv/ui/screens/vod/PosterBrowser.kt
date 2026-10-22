package app.lumentv.ui.screens.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.lumentv.ui.components.Artwork
import app.lumentv.ui.components.CategoryColumn
import app.lumentv.ui.components.Chip
import app.lumentv.ui.components.EmptyState
import app.lumentv.ui.components.FocusSurface
import app.lumentv.ui.components.Gap
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.TopBar
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.onMenuKey
import app.lumentv.ui.screens.live.CategoryItem
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

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
 * Navegador de pósters estilo app Apple TV: categorías a la izquierda, "hero" con el
 * elemento enfocado y una rejilla 2:3 de pósters. Lo comparten Cine y Series.
 */
@Composable
fun PosterBrowser(
    nav: Navigator,
    route: String,
    categories: List<CategoryItem>,
    selectedCategory: String,
    onSelectCategory: (CategoryItem) -> Unit,
    onCategoryMenu: (CategoryItem) -> Unit,
    items: List<PosterItem>,
    focused: PosterItem?,
    onFocus: (PosterItem) -> Unit,
    onOpen: (PosterItem) -> Unit,
    onItemMenu: (PosterItem) -> Unit,
    favorites: Set<String>,
    emptyTitle: String,
) {
    val c = Lumen.colors
    Box(Modifier.fillMaxSize().background(c.background)) {
        // Fondo: arte del elemento enfocado, oscurecido con degradados.
        focused?.let { f ->
            val art = f.backdrop.ifBlank { f.image }
            if (art.isNotBlank()) {
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.35f,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.6f).align(Alignment.TopEnd),
                )
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, c.background), startY = 150f, endY = 700f)))
                Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(c.background, Color.Transparent), endX = 900f)))
            }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = LumenDimens.safeHorizontal, vertical = LumenDimens.safeVertical)) {
            TopBar(nav, route)
            Gap(height = 14.dp)
            // Hero
            Column(Modifier.height(104.dp).fillMaxWidth(0.62f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                focused?.let { f ->
                    LText(f.title, style = Lumen.type.largeTitle, maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (f.rating.isNotBlank() && f.rating != "0") Chip("★ ${f.rating}", c.warning)
                        if (f.meta.isNotBlank()) LText(f.meta, style = Lumen.type.callout, color = c.textSecondary, maxLines = 1)
                    }
                    if (f.plot.isNotBlank()) LText(f.plot, style = Lumen.type.body, color = c.textSecondary, maxLines = 2)
                }
            }
            Gap(height = 8.dp)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CategoryColumn(
                    items = categories,
                    selectedId = selectedCategory,
                    onSelect = onSelectCategory,
                    onMenu = onCategoryMenu,
                    modifier = Modifier.width(200.dp).fillMaxHeight(),
                )
                if (items.isEmpty()) {
                    EmptyState(Icons.Rounded.Movie, emptyTitle, "Elige otra categoría o actualiza la lista desde Inicio.", Modifier.weight(1f))
                } else {
                    val gridState = rememberLazyGridState()
                    val firstPoster = remember { FocusRequester() }
                    var initialFocusDone by rememberSaveable { mutableStateOf(false) }
                    LaunchedEffect(items.isNotEmpty()) {
                        if (!initialFocusDone && items.isNotEmpty()) {
                            delay(150)
                            initialFocusDone = runCatching { firstPoster.requestFocus() }.isSuccess
                        }
                    }
                    LaunchedEffect(selectedCategory) { gridState.scrollToItem(0) }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(118.dp),
                        state = gridState,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentPadding = PaddingValues(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(items, key = { it.id }) { item ->
                            FocusSurface(
                                onClick = { onOpen(item) },
                                modifier = (if (item.id == items.first().id) Modifier.focusRequester(firstPoster) else Modifier)
                                    .aspectRatio(2f / 3f)
                                    .onFocusChanged { if (it.isFocused) onFocus(item) }
                                    .onMenuKey { onItemMenu(item) },
                                radius = LumenDimens.radiusMedium,
                                focusedScale = 1.08f,
                            ) {
                                Artwork(item.image, item.title, Modifier.fillMaxSize(), radius = LumenDimens.radiusMedium, contentScale = ContentScale.Crop)
                                if (item.id in favorites) Chip("Mi lista", c.accent, filled = true, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
