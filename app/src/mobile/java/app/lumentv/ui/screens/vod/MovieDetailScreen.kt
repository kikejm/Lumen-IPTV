package app.lumentv.ui.screens.vod

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import app.lumentv.ui.adaptive.LocalWidthClass
import app.lumentv.ui.adaptive.WidthClass
import app.lumentv.ui.components.Artwork
import app.lumentv.ui.components.Chip
import app.lumentv.ui.components.IconCircleButton
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

/**
 * Ficha de película (Stitch 11-ficha-pelicula). Misma carga que la TV: entidad de Room + ficha
 * `get_vod_info` bajo demanda. Vertical: arte arriba y datos debajo; horizontal: póster y arte a la
 * izquierda (40 %) y datos a la derecha.
 */
@Composable
fun MovieDetailScreen(nav: Navigator, insets: PaddingValues, movieId: String) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val wide = LocalWidthClass.current != WidthClass.Compact
    val playlist by graph.playlists.active.collectAsState(initial = null)
    var movie by remember { mutableStateOf<MovieEntity?>(null) }
    var info by remember { mutableStateOf<VodInfo?>(null) }
    var history by remember { mutableStateOf<HistoryEntity?>(null) }
    var isFav by remember { mutableStateOf(false) }
    val c = Lumen.colors

    LaunchedEffect(playlist?.id, movieId) {
        val p = playlist ?: return@LaunchedEffect
        movie = graph.catalog.movie(p.id, movieId)
        history = graph.library.historyItem(p.id, ContentKind.VOD, movieId)
        isFav = graph.library.isFavorite(p.id, ContentKind.VOD, movieId)
        if (p.type == PlaylistType.XTREAM) info = runCatching { graph.xtream.vodInfo(graph.playlists.credentials(p), movieId) }
            .onFailure { Log.w("MovieDetail", "get_vod_info falló: ${it.javaClass.simpleName}: ${it.message?.take(300)}") }
            .getOrNull()
    }

    val m = movie
    val backdrop = info?.backdrop?.ifBlank { null } ?: info?.poster?.ifBlank { null } ?: m?.poster.orEmpty()
    val poster = info?.poster?.ifBlank { null } ?: m?.poster.orEmpty()
    val h = history
    val ext = info?.containerExt ?: m?.containerExt ?: "mp4"
    val resumeAt = h?.positionMs?.takeIf { it > 30_000 && h.durationMs - it > 60_000 } ?: 0L
    fun playFrom(ms: Long) {
        val p = playlist ?: return
        val mm = movie ?: return
        graph.play(context, nav, listOf(graph.streams.movie(p, mm, ext, ms)), 0)
    }

    val details: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LText("PELÍCULA", style = Lumen.type.caption, color = c.accent)
            LText(m?.name ?: "Cargando…", style = Lumen.type.largeTitle, maxLines = 3)
            val meta = listOfNotNull(
                info?.releaseDate?.take(4)?.takeIf { it.isNotBlank() },
                info?.durationSecs?.takeIf { it > 0 }?.let { formatMinutes(it / 60) },
                info?.genre?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val rating = info?.rating?.ifBlank { null } ?: m?.rating
                if (!rating.isNullOrBlank() && rating != "0") Chip("★ $rating", c.warning)
                if (meta.isNotBlank()) LText(meta, style = Lumen.type.callout, color = c.textSecondary, maxLines = 2)
            }
            if (h != null && resumeAt > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ProgressLine(h.positionMs.toFloat() / h.durationMs)
                    LText("Quedan ${formatDuration(h.durationMs - h.positionMs)}", style = Lumen.type.caption, color = c.textSecondary)
                }
            }
            PillButton(if (resumeAt > 0) "Reanudar" else "Reproducir", onClick = { playFrom(resumeAt) }, icon = Icons.Rounded.PlayArrow, primary = true, modifier = Modifier.fillMaxWidth())
            DetailButtons {
                if (resumeAt > 0) PillButton("Desde el principio", onClick = { playFrom(0) }, icon = Icons.Rounded.Replay)
                PillButton(if (isFav) "En Mi lista" else "Mi lista", onClick = {
                    playlist?.let { p -> scope.launch { isFav = graph.library.toggleFavorite(p.id, ContentKind.VOD, movieId) } }
                }, icon = if (isFav) Icons.Rounded.Check else Icons.Rounded.Add)
                info?.trailer?.takeIf { it.isNotBlank() }?.let { trailer ->
                    PillButton("Tráiler", onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$trailer"))) }
                    }, icon = Icons.Rounded.Theaters)
                }
            }
            info?.plot?.takeIf { it.isNotBlank() }?.let {
                LText("SINOPSIS", style = Lumen.type.caption, color = c.textSecondary)
                LText(it, style = Lumen.type.body, color = c.textSecondary)
            }
            info?.director?.takeIf { it.isNotBlank() }?.let { LText("Dirección: $it", style = Lumen.type.callout, color = c.textTertiary) }
            info?.cast?.takeIf { it.isNotBlank() }?.let { LText("Reparto: $it", style = Lumen.type.callout, color = c.textTertiary) }
        }
    }

    DetailScaffold(nav, insets, wide, backdrop, poster, m?.name.orEmpty(), details)
}

/** Fila de botones secundarios que salta de línea si no caben (vertical). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailButtons(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
}

/**
 * Esqueleto común de las fichas: arte con degradado, flecha atrás y datos. En horizontal, arte y
 * póster a la izquierda (40 %) y [details] a la derecha con su propio desplazamiento.
 */
@Composable
internal fun DetailScaffold(
    nav: Navigator,
    insets: PaddingValues,
    wide: Boolean,
    backdrop: String,
    poster: String,
    title: String,
    details: @Composable () -> Unit,
    below: (@Composable () -> Unit)? = null,
) {
    val c = Lumen.colors
    if (wide) {
        Row(Modifier.fillMaxSize().padding(insets)) {
            Box(Modifier.weight(0.4f).fillMaxHeight()) {
                if (backdrop.isNotBlank()) AsyncImage(model = backdrop, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.5f to Color.Transparent, 1f to c.background)))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to c.background)))
                Artwork(poster, title, Modifier.align(Alignment.BottomStart).padding(16.dp).width(120.dp).aspectRatio(2f / 3f), radius = LumenDimens.radiusMedium, contentScale = ContentScale.Crop)
                IconCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás", onClick = { nav.back() }, modifier = Modifier.padding(12.dp), color = Color.Black.copy(alpha = 0.45f))
            }
            Column(Modifier.weight(0.6f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
                details()
                below?.invoke()
            }
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
                if (backdrop.isNotBlank()) AsyncImage(model = backdrop, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to c.background)))
                Artwork(poster, title, Modifier.align(Alignment.BottomStart).padding(16.dp).width(110.dp).aspectRatio(2f / 3f), radius = LumenDimens.radiusMedium, contentScale = ContentScale.Crop)
                // La flecha atrás respeta la barra de estado (la imagen va de borde a borde).
                Box(Modifier.padding(insets)) {
                    IconCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás", onClick = { nav.back() }, modifier = Modifier.padding(12.dp), color = Color.Black.copy(alpha = 0.45f))
                }
            }
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
                details()
                below?.invoke()
            }
        }
    }
}
