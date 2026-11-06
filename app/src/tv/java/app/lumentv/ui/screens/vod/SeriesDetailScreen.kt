package app.lumentv.ui.screens.vod

import android.util.Log
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.Layout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lumentv.core.util.formatMinutes
import app.lumentv.data.db.ContentKind
import app.lumentv.data.db.HistoryEntity
import app.lumentv.data.db.PlaylistType
import app.lumentv.data.db.SeriesEntity
import app.lumentv.data.xtream.Episode
import app.lumentv.data.xtream.SeriesInfo
import app.lumentv.ui.components.Artwork
import app.lumentv.ui.components.Chip
import app.lumentv.ui.components.FocusSurface
import app.lumentv.ui.components.Gap
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.PillButton
import app.lumentv.ui.components.ProgressLine
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.navigation.Routes
import app.lumentv.ui.play
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HEADER_ANIM_MS = 220
private val POSTER_WIDTH = 150.dp
/** Margen vertical de la fila de temporadas (deja sitio al anillo de foco). */
private val SEASON_PAD = 6.dp

@OptIn(ExperimentalFoundationApi::class) // LocalBringIntoViewSpec
@Composable
fun SeriesDetailScreen(nav: Navigator, seriesId: String) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlist by graph.playlists.active.collectAsState(initial = null)
    var series by remember { mutableStateOf<SeriesEntity?>(null) }
    var info by remember { mutableStateOf<SeriesInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var season by remember { mutableIntStateOf(-1) }
    var isFav by remember { mutableStateOf(false) }
    var lastWatched by remember { mutableStateOf<HistoryEntity?>(null) }
    val firstFocus = remember { FocusRequester() }
    // Al volver del reproductor (o de «Reanudar» en Inicio) el foco va al episodio que sonaba, en su temporada.
    val returnEpisode = remember { graph.playback.takeReturnFocus(Routes.SERIES_DETAIL)?.itemId }
    var focusEpisode by remember { mutableStateOf<String?>(null) }
    val episodeFocus = remember { FocusRequester() }
    var episodesFocused by remember { mutableStateOf(false) }
    // ▲ desde el primer episodio y ▼ desde los botones van a la temporada elegida (no a la píldora más cercana).
    val seasonFocus = remember { FocusRequester() }
    val c = Lumen.colors

    LaunchedEffect(playlist?.id, seriesId) {
        val p = playlist ?: return@LaunchedEffect
        series = graph.catalog.seriesById(p.id, seriesId)
        isFav = graph.library.isFavorite(p.id, ContentKind.SERIES, seriesId)
        if (p.type != PlaylistType.XTREAM) return@LaunchedEffect
        runCatching { graph.xtream.seriesInfo(graph.playlists.credentials(p), seriesId) }
            .onFailure { Log.w("SeriesDetail", "get_series_info falló: ${it.javaClass.simpleName}: ${it.message?.take(300)}") }
            .onSuccess { si ->
                info = si
                // Última temporada vista (o la primera).
                val watched = si.seasons.values.flatten().mapNotNull { ep -> graph.library.historyItem(p.id, ContentKind.EPISODE, ep.id) }
                lastWatched = watched.maxByOrNull { it.watchedAt }
                val lastEp = lastWatched?.let { h -> si.seasons.values.flatten().firstOrNull { it.id == h.itemId } }
                val backEp = returnEpisode?.let { id -> si.seasons.values.flatten().firstOrNull { it.id == id } }
                season = backEp?.season ?: lastEp?.season ?: si.seasons.keys.firstOrNull() ?: 1
                if (backEp != null) focusEpisode = backEp.id else runCatching { firstFocus.requestFocus() }
            }
            .onFailure { error = it.message }
    }

    fun playEpisode(eps: List<Episode>, index: Int, resumeMs: Long = 0) {
        val p = playlist ?: return
        val s = series
        val name = s?.name ?: info?.name.orEmpty()
        val cover = s?.cover ?: info?.cover.orEmpty()
        val items = eps.mapIndexed { i, ep -> graph.streams.episode(p, seriesId, name, cover, ep, if (i == index) resumeMs else 0) }
        graph.play(context, nav, items, index)
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        val backdrop = info?.backdrop?.ifBlank { null } ?: series?.backdrop?.ifBlank { null } ?: series?.cover.orEmpty()
        if (backdrop.isNotBlank()) {
            AsyncImage(model = backdrop, contentDescription = null, contentScale = ContentScale.Crop, alpha = 0.35f, modifier = Modifier.fillMaxSize())
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, c.background), startY = 200f, endY = 800f)))
        }
        // Como Netflix: al bajar a los episodios la cabecera se recoge (sin póster, sinopsis ni botones) y la lista
        // ocupa la pantalla; al subir a las temporadas vuelve a desplegarse.
        val compact = episodesFocused
        Column(Modifier.fillMaxSize().padding(start = LumenDimens.safeHorizontal, end = LumenDimens.safeHorizontal, top = LumenDimens.safeVertical)) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                AnimatedVisibility(!compact, enter = expandHorizontally(tween(HEADER_ANIM_MS)) + fadeIn(tween(HEADER_ANIM_MS)), exit = shrinkHorizontally(tween(HEADER_ANIM_MS)) + fadeOut(tween(HEADER_ANIM_MS))) {
                    Artwork(series?.cover ?: info?.cover.orEmpty(), series?.name ?: "", Modifier.width(POSTER_WIDTH).aspectRatio(2f / 3f), radius = LumenDimens.radiusLarge, contentScale = ContentScale.Crop)
                }
                // Las temporadas quedan a ras del fondo del póster (más abajo si la sinopsis es larga).
                val headerMinHeight by animateDpAsState(if (compact) 0.dp else POSTER_WIDTH * 1.5f + SEASON_PAD, tween(HEADER_ANIM_MS), label = "header")
                PinnedBottomLayout(minHeight = headerMinHeight, gap = 8.dp - SEASON_PAD, modifier = Modifier.weight(1f)) {
                    Column(Modifier.layoutId("top"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LText(series?.name ?: info?.name ?: "Cargando…", style = if (compact) Lumen.type.title else Lumen.type.largeTitle, maxLines = 1)
                        AnimatedVisibility(!compact, enter = expandVertically(tween(HEADER_ANIM_MS)) + fadeIn(tween(HEADER_ANIM_MS)), exit = shrinkVertically(tween(HEADER_ANIM_MS)) + fadeOut(tween(HEADER_ANIM_MS))) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val meta = listOf(info?.releaseDate?.take(4).orEmpty(), info?.genre ?: series?.genre.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val r = info?.rating?.ifBlank { null } ?: series?.rating
                                    if (!r.isNullOrBlank() && r != "0") Chip("★ $r", c.warning)
                                    if (meta.isNotBlank()) LText(meta, style = Lumen.type.callout, color = c.textSecondary)
                                    info?.let { LText("${it.seasons.size} temporadas", style = Lumen.type.callout, color = c.textSecondary) }
                                }
                                LText(info?.plot?.ifBlank { null } ?: series?.plot.orEmpty(), style = Lumen.type.body, color = c.textSecondary, maxLines = 3)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.focusProperties { if (info?.seasons?.isNotEmpty() == true) down = seasonFocus },
                                ) {
                                    val eps = info?.seasons?.get(season).orEmpty()
                                    val resume = lastWatched
                                    val resumeEps = resume?.let { h -> info?.seasons?.values?.firstOrNull { list -> list.any { it.id == h.itemId } } }
                                    if (resume != null && resumeEps != null) {
                                        val idx = resumeEps.indexOfFirst { it.id == resume.itemId }.coerceAtLeast(0)
                                        val ep = resumeEps[idx]
                                        PillButton("Continuar T${ep.season} · E${ep.number}", onClick = {
                                            playEpisode(resumeEps, idx, resume.positionMs)
                                        }, icon = Icons.Rounded.PlayArrow, primary = true, modifier = Modifier.focusRequester(firstFocus))
                                    } else {
                                        PillButton("Reproducir", onClick = { if (eps.isNotEmpty()) playEpisode(eps, 0) }, icon = Icons.Rounded.PlayArrow, primary = true, modifier = Modifier.focusRequester(firstFocus))
                                    }
                                    PillButton(if (isFav) "En Mi lista" else "Mi lista", onClick = {
                                        playlist?.let { p -> scope.launch { isFav = graph.library.toggleFavorite(p.id, ContentKind.SERIES, seriesId) } }
                                    }, icon = if (isFav) Icons.Rounded.Check else Icons.Rounded.Add)
                                }
                            }
                        }
                    }
                    // Temporadas bajo los botones; con la cabecera recogida, bajo el título.
                    val seasons = info?.seasons?.keys?.toList().orEmpty()
                    if (seasons.isNotEmpty()) {
                        LazyRow(Modifier.layoutId("bottom"), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = SEASON_PAD, horizontal = 4.dp)) {
                            items(seasons) { s ->
                                val sel = s == season
                                FocusSurface(
                                    onClick = { season = s },
                                    modifier = if (sel) Modifier.focusRequester(seasonFocus) else Modifier,
                                    radius = 50.dp,
                                    containerColor = if (sel) c.accent else c.surface,
                                    focusedContainerColor = if (sel) c.accent else c.surfaceFocused,
                                    contentColor = if (sel) c.onAccent else c.textPrimary,
                                    focusedContentColor = if (sel) c.onAccent else c.textPrimary,
                                ) { LText("Temporada $s", style = Lumen.type.callout, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                            }
                        }
                    }
                }
            }
            Gap(height = 14.dp)
            error?.let { LText("No se pudieron cargar los episodios: $it", style = Lumen.type.body, color = c.danger) }
            val eps = info?.seasons?.get(season).orEmpty()
            if (eps.isNotEmpty()) {
                val episodesState = rememberLazyListState()
                LaunchedEffect(focusEpisode, eps) {
                    val index = eps.indexOfFirst { it.id == focusEpisode }
                    if (index < 0) return@LaunchedEffect
                    episodesState.scrollToItem(index)
                    for (attempt in 0 until 10) {
                        if (runCatching { episodeFocus.requestFocus() }.isSuccess) break
                        delay(30)
                    }
                    focusEpisode = null
                }
                // La lista llega hasta el borde inferior; al bajar, el episodio enfocado se para antes del margen de overscan.
                val safeBottom = with(LocalDensity.current) { LumenDimens.safeVertical.toPx() }
                CompositionLocalProvider(LocalBringIntoViewSpec provides remember(safeBottom) { BottomMarginBringIntoView(safeBottom) }) {
                    LazyColumn(
                        state = episodesState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = LumenDimens.safeVertical),
                        modifier = Modifier.weight(1f).onFocusChanged { episodesFocused = it.hasFocus },
                    ) {
                        itemsIndexed(eps, key = { _, e -> e.id }) { i, ep ->
                            val rowModifier = (if (ep.id == focusEpisode) Modifier.focusRequester(episodeFocus) else Modifier)
                                .then(if (i == 0) Modifier.focusProperties { up = seasonFocus } else Modifier)
                            EpisodeRow(ep, playlist?.id ?: -1, rowModifier) { resumeMs ->
                                playEpisode(eps, i, resumeMs)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: Episode, pid: Long, modifier: Modifier, onPlay: (Long) -> Unit) {
    val graph = LocalGraph.current
    val c = Lumen.colors
    var history by remember { mutableStateOf<HistoryEntity?>(null) }
    LaunchedEffect(ep.id, pid) { history = graph.library.historyItem(pid, ContentKind.EPISODE, ep.id) }
    FocusSurface(onClick = { onPlay(history?.positionMs?.takeIf { it > 30_000 } ?: 0) }, modifier = modifier.fillMaxWidth(), focusedScale = 1.02f) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(ep.image, "E${ep.number}", Modifier.width(150.dp).aspectRatio(16f / 9f), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LText("${ep.number}. ${ep.title.ifBlank { "Episodio ${ep.number}" }}", style = Lumen.type.headline, maxLines = 1)
                if (ep.durationSecs > 0) LText(formatMinutes(ep.durationSecs / 60), style = Lumen.type.caption, color = c.textSecondary)
                if (ep.plot.isNotBlank()) LText(ep.plot, style = Lumen.type.body, color = c.textSecondary, maxLines = 2)
                history?.takeIf { it.durationMs > 0 }?.let { ProgressLine(it.positionMs.toFloat() / it.durationMs, Modifier.width(240.dp)) }
            }
        }
    }
}

/** Hijo `top` arriba y `bottom` pegado al fondo, con al menos [minHeight] de alto. */
@Composable
private fun PinnedBottomLayout(minHeight: Dp, gap: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val top = measurables.firstOrNull { it.layoutId == "top" }?.measure(loose)
        val bottom = measurables.firstOrNull { it.layoutId == "bottom" }?.measure(loose)
        val topH = top?.height ?: 0
        val bottomH = bottom?.height ?: 0
        val gapPx = if (bottom != null) gap.roundToPx() else 0
        val height = maxOf(minHeight.roundToPx(), topH + gapPx + bottomH).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(constraints.maxWidth, height) {
            top?.place(0, 0)
            bottom?.place(0, height - bottomH)
        }
    }
}

/** Desplaza lo justo para que el elemento enfocado quede visible y fuera de los últimos [bottomPx] del contenedor. */
@OptIn(ExperimentalFoundationApi::class)
private class BottomMarginBringIntoView(private val bottomPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val end = containerSize - bottomPx
        return when {
            offset < 0f -> offset
            offset + size > end -> minOf(offset, offset + size - end)
            else -> 0f
        }
    }
}
