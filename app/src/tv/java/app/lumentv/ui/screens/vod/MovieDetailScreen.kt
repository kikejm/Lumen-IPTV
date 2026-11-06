package app.lumentv.ui.screens.vod

import android.content.Intent
import android.util.Log
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lumentv.core.util.formatDuration
import app.lumentv.core.util.formatMinutes
import app.lumentv.data.db.ContentKind
import app.lumentv.data.db.HistoryEntity
import app.lumentv.data.db.MovieEntity
import app.lumentv.data.db.PlaylistType
import app.lumentv.data.xtream.VodInfo
import app.lumentv.ui.components.Artwork
import app.lumentv.ui.components.Chip
import app.lumentv.ui.components.Gap
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.PillButton
import app.lumentv.ui.components.ProgressLine
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.play
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun MovieDetailScreen(nav: Navigator, movieId: String) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playlist by graph.playlists.active.collectAsState(initial = null)
    var movie by remember { mutableStateOf<MovieEntity?>(null) }
    var info by remember { mutableStateOf<VodInfo?>(null) }
    var history by remember { mutableStateOf<HistoryEntity?>(null) }
    var isFav by remember { mutableStateOf(false) }
    val playFocus = remember { FocusRequester() }
    val c = Lumen.colors

    LaunchedEffect(playlist?.id, movieId) {
        val p = playlist ?: return@LaunchedEffect
        movie = graph.catalog.movie(p.id, movieId)
        history = graph.library.historyItem(p.id, ContentKind.VOD, movieId)
        isFav = graph.library.isFavorite(p.id, ContentKind.VOD, movieId)
        runCatching { playFocus.requestFocus() }
        if (p.type == PlaylistType.XTREAM) info = runCatching { graph.xtream.vodInfo(graph.playlists.credentials(p), movieId) }
            .onFailure { Log.w("MovieDetail", "get_vod_info falló: ${it.javaClass.simpleName}: ${it.message?.take(300)}") }
            .getOrNull()
    }

    val m = movie
    Box(Modifier.fillMaxSize().background(c.background)) {
        val backdrop = info?.backdrop?.ifBlank { null } ?: info?.poster ?: m?.poster.orEmpty()
        if (backdrop.isNotBlank()) {
            AsyncImage(model = backdrop, contentDescription = null, contentScale = ContentScale.Crop, alpha = 0.45f, modifier = Modifier.fillMaxSize())
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(c.background, c.background.copy(alpha = 0.6f), Color.Transparent))))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, c.background), startY = 400f)))
        }
        Row(
            Modifier.fillMaxSize().padding(horizontal = LumenDimens.safeHorizontal + 12.dp, vertical = LumenDimens.safeVertical + 24.dp),
            horizontalArrangement = Arrangement.spacedBy(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(info?.poster?.ifBlank { null } ?: m?.poster.orEmpty(), m?.name ?: "", Modifier.width(220.dp).aspectRatio(2f / 3f), radius = LumenDimens.radiusLarge, contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LText(m?.name ?: "Cargando…", style = Lumen.type.largeTitle, maxLines = 2)
                val meta = listOfNotNull(
                    info?.releaseDate?.take(4)?.takeIf { it.isNotBlank() },
                    info?.durationSecs?.takeIf { it > 0 }?.let { formatMinutes(it / 60) },
                    info?.genre?.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val rating = info?.rating?.ifBlank { null } ?: m?.rating
                    if (!rating.isNullOrBlank() && rating != "0") Chip("★ $rating", c.warning)
                    if (meta.isNotBlank()) LText(meta, style = Lumen.type.callout, color = c.textSecondary)
                }
                info?.plot?.takeIf { it.isNotBlank() }?.let { LText(it, style = Lumen.type.body, color = c.textSecondary, maxLines = 5) }
                info?.cast?.takeIf { it.isNotBlank() }?.let { LText("Reparto: $it", style = Lumen.type.callout, color = c.textTertiary, maxLines = 2) }
                info?.director?.takeIf { it.isNotBlank() }?.let { LText("Dirección: $it", style = Lumen.type.callout, color = c.textTertiary, maxLines = 1) }

                val h = history
                if (h != null && h.durationMs > 0 && h.positionMs > 30_000) {
                    Column(Modifier.width(360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ProgressLine(h.positionMs.toFloat() / h.durationMs)
                        LText("Quedan ${formatDuration(h.durationMs - h.positionMs)}", style = Lumen.type.caption, color = c.textSecondary)
                    }
                }
                Gap(height = 8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val p = playlist
                    val ext = info?.containerExt ?: m?.containerExt ?: "mp4"
                    val resumeAt = h?.positionMs?.takeIf { it > 30_000 && h.durationMs - it > 60_000 } ?: 0L
                    PillButton(
                        if (resumeAt > 0) "Reanudar" else "Reproducir",
                        onClick = { if (p != null && m != null) graph.play(context, nav, listOf(graph.streams.movie(p, m, ext, resumeAt)), 0) },
                        icon = Icons.Rounded.PlayArrow,
                        primary = true,
                        modifier = Modifier.focusRequester(playFocus),
                    )
                    if (resumeAt > 0) {
                        PillButton("Desde el principio", onClick = {
                            if (p != null && m != null) graph.play(context, nav, listOf(graph.streams.movie(p, m, ext, 0)), 0)
                        }, icon = Icons.Rounded.Replay)
                    }
                    PillButton(if (isFav) "En Mi lista" else "Mi lista", onClick = {
                        if (p != null) scope.launch { isFav = graph.library.toggleFavorite(p.id, ContentKind.VOD, movieId) }
                    }, icon = if (isFav) Icons.Rounded.Check else Icons.Rounded.Add)
                    info?.trailer?.takeIf { it.isNotBlank() }?.let { trailer ->
                        PillButton("Tráiler", onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$trailer")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }, icon = Icons.Rounded.Theaters)
                    }
                }
            }
        }
    }
}
