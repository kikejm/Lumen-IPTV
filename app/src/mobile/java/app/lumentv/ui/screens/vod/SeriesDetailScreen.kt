package app.lumentv.ui.screens.vod

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import app.lumentv.ui.adaptive.LocalWidthClass
import app.lumentv.ui.adaptive.WidthClass
import app.lumentv.ui.components.Artwork
import app.lumentv.ui.components.Chip
import app.lumentv.ui.components.FilterChip
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.PillButton
import app.lumentv.ui.components.ProgressLine
import app.lumentv.ui.components.TapSurface
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.play
import app.lumentv.ui.theme.Lumen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Ficha de serie (Stitch 05-ficha-serie / L05-ficha-serie): datos, «Continuar T·E», temporadas en
 * píldoras y episodios con progreso. Misma carga que la TV (`get_series_info` bajo demanda).
 */
@Composable
fun SeriesDetailScreen(nav: Navigator, insets: PaddingValues, seriesId: String) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val wide = LocalWidthClass.current != WidthClass.Compact
    val playlist by graph.playlists.active.collectAsState(initial = null)
    var series by remember { mutableStateOf<SeriesEntity?>(null) }
    var info by remember { mutableStateOf<SeriesInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var season by rememberSaveable { mutableIntStateOf(-1) }
    var isFav by remember { mutableStateOf(false) }
    // Progreso de los episodios en una sola consulta que se actualiza sola (al volver del
    // reproductor ya sale el minuto nuevo); antes eran dos consultas por episodio.
    val episodeHistory by remember(playlist?.id) {
        playlist?.let { p -> graph.library.history(p.id, ContentKind.EPISODE, limit = 1000).map { list -> list.associateBy { it.itemId } } }
            ?: flowOf(emptyMap())
    }.collectAsState(initial = emptyMap())
    val lastWatched = remember(info, episodeHistory) {
        info?.seasons?.values?.flatten()?.mapNotNull { episodeHistory[it.id] }?.maxByOrNull { it.watchedAt }
    }
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
                // Última temporada vista (o la primera), salvo que ya se eligiera otra antes de girar.
                if (season == -1) {
                    val history = graph.library.history(p.id, ContentKind.EPISODE, limit = 1000).first().associateBy { it.itemId }
                    val lastEp = si.seasons.values.flatten().filter { it.id in history }.maxByOrNull { history.getValue(it.id).watchedAt }
                    season = lastEp?.season ?: si.seasons.keys.firstOrNull() ?: 1
                }
            }
            .onFailure { error = it.message }
    }

    fun playEpisode(eps: List<Episode>, index: Int, resumeMs: Long = 0) {
        val p = playlist ?: return
        val name = series?.name ?: info?.name.orEmpty()
        val cover = series?.cover ?: info?.cover.orEmpty()
        val items = eps.mapIndexed { i, ep -> graph.streams.episode(p, seriesId, name, cover, ep, if (i == index) resumeMs else 0) }
        graph.play(context, nav, items, index)
    }

    val backdrop = info?.backdrop?.ifBlank { null } ?: series?.backdrop?.ifBlank { null } ?: series?.cover.orEmpty()
    val poster = series?.cover ?: info?.cover.orEmpty()
    val title = series?.name ?: info?.name ?: "Cargando…"

    val details: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LText("SERIE", style = Lumen.type.caption, color = c.accent)
            LText(title, style = Lumen.type.largeTitle, maxLines = 3)
            val meta = listOf(info?.releaseDate?.take(4).orEmpty(), info?.genre ?: series?.genre.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val r = info?.rating?.ifBlank { null } ?: series?.rating
                if (!r.isNullOrBlank() && r != "0") Chip("★ $r", c.warning)
                info?.let { Chip("${it.seasons.size} temporadas", c.textSecondary) }
                if (meta.isNotBlank()) LText(meta, style = Lumen.type.callout, color = c.textSecondary, maxLines = 1)
            }
            val plot = info?.plot?.ifBlank { null } ?: series?.plot.orEmpty()
            if (plot.isNotBlank()) LText(plot, style = Lumen.type.body, color = c.textSecondary, maxLines = 4)

            val resume = lastWatched
            val resumeEps = resume?.let { h -> info?.seasons?.values?.firstOrNull { list -> list.any { it.id == h.itemId } } }
            val currentEps = info?.seasons?.get(season).orEmpty()
            if (resume != null && resumeEps != null) {
                val idx = resumeEps.indexOfFirst { it.id == resume.itemId }.coerceAtLeast(0)
                val ep = resumeEps[idx]
                // Un episodio visto hasta el final no se «reanuda» en los créditos: se pasa al siguiente
                // (de la misma temporada o la primera de la siguiente) o, si era el último, se vuelve a ver.
                val finished = resume.durationMs > 0 && resume.positionMs >= resume.durationMs * 95 / 100
                val nextSeasonEps = info?.seasons?.entries?.sortedBy { it.key }?.firstOrNull { it.key > ep.season }?.value.orEmpty()
                when {
                    !finished -> PillButton("Reanudar T${ep.season} · E${ep.number}", onClick = { playEpisode(resumeEps, idx, resume.positionMs) }, icon = Icons.Rounded.PlayArrow, primary = true, modifier = Modifier.fillMaxWidth())
                    idx + 1 < resumeEps.size -> resumeEps[idx + 1].let { next ->
                        PillButton("Siguiente: T${next.season} · E${next.number}", onClick = { playEpisode(resumeEps, idx + 1) }, icon = Icons.Rounded.PlayArrow, primary = true, modifier = Modifier.fillMaxWidth())
                    }
                    nextSeasonEps.isNotEmpty() -> nextSeasonEps.first().let { next ->
                        PillButton("Siguiente: T${next.season} · E${next.number}", onClick = { playEpisode(nextSeasonEps, 0) }, icon = Icons.Rounded.PlayArrow, primary = true, modifier = Modifier.fillMaxWidth())
                    }
                    else -> PillButton("Volver a ver T${ep.season} · E${ep.number}", onClick = { playEpisode(resumeEps, idx) }, icon = Icons.Rounded.PlayArrow, primary = true, modifier = Modifier.fillMaxWidth())
                }
            } else {
                PillButton("Reproducir", onClick = { if (currentEps.isNotEmpty()) playEpisode(currentEps, 0) }, icon = Icons.Rounded.PlayArrow, primary = true, enabled = currentEps.isNotEmpty(), modifier = Modifier.fillMaxWidth())
            }
            DetailButtons {
                PillButton(if (isFav) "En Mi lista" else "Mi lista", onClick = {
                    playlist?.let { p -> scope.launch { isFav = graph.library.toggleFavorite(p.id, ContentKind.SERIES, seriesId) } }
                }, icon = if (isFav) Icons.Rounded.Check else Icons.Rounded.Add)
            }
        }
    }

    val episodes: @Composable () -> Unit = {
        Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            error?.let { LText("No se pudieron cargar los episodios: $it", style = Lumen.type.body, color = c.danger) }
            val seasons = info?.seasons?.keys?.toList().orEmpty()
            if (seasons.isNotEmpty()) {
                LText("TEMPORADAS", style = Lumen.type.caption, color = c.textSecondary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(seasons) { s -> FilterChip("T$s", selected = s == season, onClick = { season = s }) }
                }
                val eps = info?.seasons?.get(season).orEmpty()
                // Dentro de una columna desplazable: los episodios van en una Column (una temporada son decenas, no miles).
                eps.forEachIndexed { i, ep ->
                    EpisodeRow(ep, episodeHistory[ep.id]) { resumeMs -> playEpisode(eps, i, resumeMs) }
                }
            }
        }
    }

    DetailScaffold(nav, insets, wide, backdrop, poster, title, details, below = episodes)
}

@Composable
private fun EpisodeRow(ep: Episode, history: HistoryEntity?, onPlay: (Long) -> Unit) {
    val c = Lumen.colors
    TapSurface(onClick = { onPlay(history?.positionMs?.takeIf { it > 30_000 } ?: 0) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(128.dp).aspectRatio(16f / 9f)) {
                Artwork(ep.image, "E${ep.number}", Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentScale = ContentScale.Crop)
                history?.takeIf { it.durationMs > 0 }?.let {
                    ProgressLine(it.positionMs.toFloat() / it.durationMs, Modifier.align(Alignment.BottomCenter), color = c.accent)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                LText("E${ep.number} · ${ep.title.ifBlank { "Episodio ${ep.number}" }}", style = Lumen.type.headline, maxLines = 2)
                val sub = listOfNotNull(
                    ep.durationSecs.takeIf { it > 0 }?.let { formatMinutes(it / 60) },
                    history?.takeIf { it.durationMs > 0 }?.let { h -> "En progreso · ${(h.positionMs * 100 / h.durationMs).toInt()} %" },
                ).joinToString(" · ")
                if (sub.isNotBlank()) LText(sub, style = Lumen.type.caption, color = if (history != null) c.accent else c.textSecondary)
                if (ep.plot.isNotBlank()) LText(ep.plot, style = Lumen.type.callout, color = c.textSecondary, maxLines = 2)
            }
        }
    }
}
