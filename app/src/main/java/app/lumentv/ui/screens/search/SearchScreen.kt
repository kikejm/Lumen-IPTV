package app.lumentv.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.lumentv.data.catalog.SearchResults
import app.lumentv.ui.components.Artwork
import app.lumentv.ui.components.EmptyState
import app.lumentv.ui.components.FocusSurface
import app.lumentv.ui.components.Gap
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.SectionTitle
import app.lumentv.ui.components.TopBar
import app.lumentv.ui.components.TvTextField
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.navigation.Routes
import app.lumentv.ui.playChannels
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.tv.material3.Icon
import app.lumentv.data.db.ChannelEntity
import app.lumentv.data.db.ContentKind
import app.lumentv.ui.components.KeyHints
import app.lumentv.ui.components.MenuOption
import app.lumentv.ui.components.OptionsDialog
import app.lumentv.ui.onMenuKey
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Búsqueda maestra en canales, películas y series de la lista activa. */
@Composable
fun SearchScreen(nav: Navigator) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val playlist by graph.playlists.active.collectAsState(initial = null)
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(SearchResults()) }
    val field = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val favorites by remember(playlist?.id) {
        playlist?.let { graph.library.favoriteIds(it.id, ContentKind.LIVE) } ?: flowOf(emptySet())
    }.collectAsState(initial = emptySet())
    var channelMenu by remember { mutableStateOf<ChannelEntity?>(null) }
    val c = Lumen.colors

    LaunchedEffect(query, playlist?.id) {
        delay(300)
        results = playlist?.let { graph.catalog.search(it.id, query) } ?: SearchResults()
    }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }

    Column(
        Modifier.fillMaxSize().background(c.background)
            .padding(horizontal = LumenDimens.safeHorizontal, vertical = LumenDimens.safeVertical),
    ) {
        TopBar(nav, Routes.SEARCH, title = "Búsqueda maestra")
        Gap(height = 18.dp)
        TvTextField(query, { query = it }, "Buscar en canales, películas y series", placeholder = "Escribe al menos 2 letras", imeAction = ImeAction.Search, focusRequester = field, modifier = Modifier.fillMaxWidth(0.6f))
        Gap(height = 18.dp)
        if (query.length >= 2 && results.isEmpty) {
            EmptyState(Icons.Rounded.Search, "Sin resultados", "Prueba con otro término.")
            return@Column
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (results.channels.isNotEmpty()) item {
                Column {
                    SectionTitle("Canales (${results.channels.size})")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(4.dp)) {
                        items(results.channels, key = { it.streamId }) { ch ->
                            // Mantener OK (o ☰) abre el menú de favoritos.
                            FocusSurface(
                                onClick = { playlist?.let { graph.playChannels(context, nav, it, results.channels, results.channels.indexOf(ch)) } },
                                onLongClick = { channelMenu = ch },
                                modifier = Modifier.width(170.dp).onMenuKey { channelMenu = ch },
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Artwork(ch.logo, ch.name, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                                    Gap(height = 6.dp)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        LText(ch.name, style = Lumen.type.caption, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                                        if (ch.streamId in favorites) Icon(Icons.Rounded.Favorite, contentDescription = "Favorito", tint = c.danger, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (results.movies.isNotEmpty()) item {
                Column {
                    SectionTitle("Películas (${results.movies.size})")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(4.dp)) {
                        items(results.movies, key = { it.streamId }) { m ->
                            PosterTile(m.name, m.poster) { nav.go(Routes.movie(m.streamId)) }
                        }
                    }
                }
            }
            if (results.series.isNotEmpty()) item {
                Column {
                    SectionTitle("Series (${results.series.size})")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(4.dp)) {
                        items(results.series, key = { it.seriesId }) { s ->
                            PosterTile(s.name, s.cover) { nav.go(Routes.series(s.seriesId)) }
                        }
                    }
                }
            }
        }
        if (results.channels.isNotEmpty()) KeyHints("OK" to "Ver", "☰" to "Favoritos (o mantén OK)")
    }

    channelMenu?.let { ch ->
        val isFav = ch.streamId in favorites
        OptionsDialog(
            ch.name,
            listOf(
                MenuOption(if (isFav) "Quitar de favoritos" else "Añadir a favoritos", if (isFav) Icons.Rounded.FavoriteBorder else Icons.Rounded.Favorite) {
                    playlist?.let { p -> scope.launch { graph.library.toggleFavorite(p.id, ContentKind.LIVE, ch.streamId) } }
                },
            ),
            onDismiss = { channelMenu = null },
        )
    }
}

@Composable
private fun PosterTile(title: String, image: String, onClick: () -> Unit) {
    Column(Modifier.width(110.dp)) {
        FocusSurface(onClick = onClick, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f), focusedScale = 1.08f) {
            Artwork(image, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Row(Modifier.padding(top = 6.dp)) { LText(title, style = Lumen.type.caption, maxLines = 1) }
    }
}
