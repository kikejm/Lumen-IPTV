package app.lumentv.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.lumentv.data.catalog.SearchResults
import app.lumentv.data.db.ChannelEntity
import app.lumentv.data.db.ContentKind
import app.lumentv.ui.components.Artwork
import app.lumentv.ui.components.EmptyState
import app.lumentv.ui.components.FilterChip
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.LumenTextField
import app.lumentv.ui.components.OptionsSheet
import app.lumentv.ui.components.SectionTitle
import app.lumentv.ui.components.SheetOption
import app.lumentv.ui.components.TapSurface
import app.lumentv.ui.components.TopBar
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.navigation.Routes
import app.lumentv.ui.playChannels
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private enum class SearchFilter(val label: String) { ALL("Todo"), CHANNELS("Canales"), MOVIES("Películas"), SERIES("Series") }

/**
 * Búsqueda maestra (Stitch 12-busqueda) en canales, películas y series de la lista activa, con la
 * misma consulta que la TV (`CatalogRepository.search`). El teclado se abre al entrar.
 */
@Composable
fun SearchScreen(nav: Navigator, insets: PaddingValues) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlist by graph.playlists.active.collectAsState(initial = null)
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(SearchFilter.ALL) }
    var results by remember { mutableStateOf(SearchResults()) }
    val favorites by remember(playlist?.id) {
        playlist?.let { graph.library.favoriteIds(it.id, ContentKind.LIVE) } ?: flowOf(emptySet())
    }.collectAsState(initial = emptySet())
    var channelMenu by remember { mutableStateOf<ChannelEntity?>(null) }
    val field = remember { FocusRequester() }
    val c = Lumen.colors

    LaunchedEffect(query, playlist?.id) {
        delay(300)
        results = playlist?.let { graph.catalog.search(it.id, query) } ?: SearchResults()
    }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }

    val showChannels = filter == SearchFilter.ALL || filter == SearchFilter.CHANNELS
    val showMovies = filter == SearchFilter.ALL || filter == SearchFilter.MOVIES
    val showSeries = filter == SearchFilter.ALL || filter == SearchFilter.SERIES

    Column(Modifier.fillMaxSize().padding(insets).imePadding()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            TopBar(nav, "Buscar", showBack = true, showActions = false)
            LumenTextField(
                query, { query = it }, "Canales, películas y series",
                placeholder = "Escribe al menos 2 letras", imeAction = ImeAction.Search,
                focusRequester = field,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LazyRow(contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SearchFilter.entries.toList()) { f -> FilterChip(f.label, selected = f == filter, onClick = { filter = f }) }
        }
        when {
            query.trim().length < 2 -> EmptyState(Icons.Rounded.Search, "Busca en tu lista", "Canales, películas y series de la lista activa.")
            results.isEmpty -> EmptyState(Icons.Rounded.Search, "Sin resultados", "Prueba con otro término.")
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (showChannels && results.channels.isNotEmpty()) {
                    item { SectionTitle("Canales (${results.channels.size})") }
                    items(results.channels, key = { "ch-${it.streamId}" }) { ch ->
                        TapSurface(
                            onClick = { playlist?.let { graph.playChannels(context, nav, it, results.channels, results.channels.indexOf(ch)) } },
                            onLongClick = { channelMenu = ch },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            radius = LumenDimens.radiusSmall,
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Artwork(ch.logo, ch.name, Modifier.size(width = 52.dp, height = 34.dp), radius = 6.dp)
                                LText(ch.name, style = Lumen.type.callout, maxLines = 1, modifier = Modifier.weight(1f))
                                if (ch.streamId in favorites) Icon(Icons.Rounded.Favorite, contentDescription = "Favorito", tint = c.accent, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                if (showMovies && results.movies.isNotEmpty()) item {
                    Column {
                        SectionTitle("Películas (${results.movies.size})")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(results.movies, key = { it.streamId }) { m -> PosterTile(m.name, m.poster) { nav.go(Routes.movie(m.streamId)) } }
                        }
                    }
                }
                if (showSeries && results.series.isNotEmpty()) item {
                    Column {
                        SectionTitle("Series (${results.series.size})")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(results.series, key = { it.seriesId }) { s -> PosterTile(s.name, s.cover) { nav.go(Routes.series(s.seriesId)) } }
                        }
                    }
                }
            }
        }
    }

    channelMenu?.let { ch ->
        val isFav = ch.streamId in favorites
        OptionsSheet(ch.name, listOf(
            SheetOption(if (isFav) "Quitar de favoritos" else "Añadir a favoritos", if (isFav) Icons.Rounded.FavoriteBorder else Icons.Rounded.Favorite) {
                playlist?.let { p -> scope.launch { graph.library.toggleFavorite(p.id, ContentKind.LIVE, ch.streamId) } }
            },
        ), onDismiss = { channelMenu = null })
    }
}

@Composable
private fun PosterTile(title: String, image: String, onClick: () -> Unit) {
    Column(Modifier.width(104.dp)) {
        TapSurface(onClick = onClick, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f), color = Color.Transparent) {
            Artwork(image, title, Modifier.fillMaxSize(), radius = LumenDimens.radiusMedium, contentScale = ContentScale.Crop)
        }
        Box(Modifier.padding(top = 6.dp)) { LText(title, style = Lumen.type.caption, maxLines = 1) }
    }
}
