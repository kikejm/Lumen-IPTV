package app.lumentv.ui.screens.live

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import app.lumentv.core.util.TimeFormatter
import app.lumentv.data.catalog.QualityFilter
import app.lumentv.data.db.ChannelEntity
import app.lumentv.data.db.ContentKind
import app.lumentv.data.db.PlaylistEntity
import app.lumentv.data.epg.Program
import app.lumentv.player.PlayerFactory
import app.lumentv.ui.adaptive.LocalWidthClass
import app.lumentv.ui.catchupRoute
import app.lumentv.ui.adaptive.WidthClass
import app.lumentv.ui.components.Artwork
import app.lumentv.ui.components.EmptyState
import app.lumentv.ui.components.FilterChip
import app.lumentv.ui.components.Gap
import app.lumentv.ui.components.IconCircleButton
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.LoadingSpinner
import app.lumentv.ui.components.OptionsSheet
import app.lumentv.ui.components.PinSheet
import app.lumentv.ui.components.ProgressLine
import app.lumentv.ui.components.SheetOption
import app.lumentv.ui.components.TapSurface
import app.lumentv.ui.components.TopBar
import app.lumentv.ui.components.VideoSurface
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.navigation.Routes
import app.lumentv.ui.playChannels
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TV en directo (Stitch 02-directo / L02-directo / T02-directo). Misma lógica que la TV
 * (`LiveViewModel`, `PlayerFactory.claimStream`): tocar un canal lo pasa a la previsualización y
 * tocar el que ya suena (o el vídeo) lo abre a pantalla completa. Pulsación larga o «⋯» = opciones.
 *
 * - Vertical: previsualización arriba, chips de categoría y calidad, lista.
 * - Horizontal: lista (40 %) · previsualización + EPG (60 %).
 * - Tableta: categorías · lista · previsualización + EPG.
 */
@Composable
fun LiveScreen(nav: Navigator, insets: PaddingValues) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val widthClass = LocalWidthClass.current
    val vm: LiveViewModel = viewModel { LiveViewModel(graph) }
    val playlist by vm.playlist.collectAsState()
    val categories by vm.categories.collectAsState()
    val selected by vm.selectedCategory.collectAsState()
    val page by vm.channelPage.collectAsState()
    val channels = page.channels
    val quality by vm.qualityFilter.collectAsState()
    val qualities by vm.availableQualities.collectAsState()
    val favorites by vm.favoriteIds.collectAsState()
    val programs by vm.focusedPrograms.collectAsState()
    val preview by vm.previewChannel.collectAsState()
    val settings by graph.settings.settings.collectAsState()
    val fmt = remember(settings.use24h, settings.epgOffsetHours) { TimeFormatter(settings.use24h, settings.epgOffsetHours * 3_600_000L) }

    var pinFor by remember { mutableStateOf<CategoryItem?>(null) }
    var categoryMenu by remember { mutableStateOf<CategoryItem?>(null) }
    var channelMenu by remember { mutableStateOf<ChannelEntity?>(null) }

    // Reproductor de la previsualización. Vive con la pantalla (por encima de la maquetación), así
    // que al girar el móvil el vídeo sigue sonando aunque su vista se vuelva a crear.
    val player = remember { graph.players.create() }
    var previewState by remember { mutableIntStateOf(player.playbackState) }
    var previewFailed by remember { mutableStateOf(false) }
    var previewRetry by remember { mutableIntStateOf(0) }
    var previewReconnect by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                previewState = state
                // Un directo que termina es un corte de emisión: se vuelve a conectar.
                if (state == Player.STATE_ENDED) previewReconnect++
            }
            override fun onPlayerError(error: PlaybackException) {
                if (previewRetry < 1) previewRetry++ else previewFailed = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(preview) { previewRetry = 0 }
    LaunchedEffect(preview, playlist, previewRetry, previewReconnect) {
        val p = playlist ?: return@LaunchedEffect
        val ch = preview ?: return@LaunchedEffect
        val url = graph.streams.live(p, ch).url
        previewFailed = false
        if (previewRetry == 0 && PlayerFactory.isLoaded(player, url)) {
            player.playWhenReady = true
            return@LaunchedEffect
        }
        if (previewRetry > 0) delay(2_000)
        // Una sola conexión de vídeo por cuenta: para cualquier otro reproductor antes de empezar.
        graph.players.claimStream(player)
        player.setMediaItem(PlayerFactory.mediaItem(url))
        player.prepare()
        player.playWhenReady = true
    }
    // Al entrar, previsualiza el primer canal (como la TV con el primero que recibe el foco).
    LaunchedEffect(channels.firstOrNull()?.streamId) { channels.firstOrNull()?.let(vm::onChannelFocused) }

    fun watch(list: List<ChannelEntity>, index: Int) {
        val p = playlist ?: return
        // El traspaso del reproductor sin recargar (`PlayerFactory.handOff`) llega con el reproductor móvil (fase 4).
        graph.playChannels(context, nav, p, list, index)
    }
    fun tapChannel(list: List<ChannelEntity>, index: Int) {
        val ch = list[index]
        if (ch.streamId == preview?.streamId) watch(list, index) else vm.showNow(ch)
    }
    fun watchPreview() {
        val ch = preview ?: return
        val index = channels.indexOfFirst { it.streamId == ch.streamId }
        if (index >= 0) watch(channels, index) else watch(listOf(ch), 0)
    }
    fun select(cat: CategoryItem) {
        if (vm.isCategoryLocked(cat.id)) pinFor = cat else vm.selectedCategory.value = cat.id
    }

    val previewBox: @Composable (Modifier) -> Unit = { modifier ->
        PreviewVideo(
            player = player,
            channel = preview,
            loading = previewState != Player.STATE_READY,
            failed = previewFailed,
            onClick = ::watchPreview,
            modifier = modifier,
        )
    }
    val categoryChips: @Composable () -> Unit = {
        CategoryChips(categories, selected, onSelect = ::select, onMenu = { if (it.id !in listOf(CAT_ALL, CAT_FAVORITES, CAT_RECENT)) categoryMenu = it })
    }
    val qualityChips: @Composable () -> Unit = {
        if (qualities.size > 1 || quality != QualityFilter.ALL) {
            QualityChips(if (quality in qualities) qualities else qualities + quality, quality, vm::setQuality)
        }
    }
    val channelList: @Composable (Modifier) -> Unit = { modifier ->
        ChannelList(
            channels = channels,
            playlist = playlist,
            favorites = favorites,
            previewId = preview?.streamId,
            fmt = fmt,
            emptyTitle = when {
                selected == CAT_FAVORITES -> "Sin favoritos"
                selected == CAT_RECENT -> "Sin historial"
                quality != QualityFilter.ALL -> "Ningún canal ${quality.label}"
                else -> "Sin canales"
            },
            emptyMessage = when {
                selected == CAT_FAVORITES -> "Mantén pulsado un canal (o toca ⋯) y elige «Añadir a favoritos»."
                quality != QualityFilter.ALL && selected != CAT_RECENT -> "Esta categoría no tiene canales en esa calidad. Elige «Todas»."
                else -> "Actualiza la lista desde Inicio si acabas de añadirla."
            },
            resetKey = selected to quality,
            onTap = ::tapChannel,
            onMenu = { channelMenu = it },
            modifier = modifier,
        )
    }

    Column(Modifier.fillMaxSize().padding(insets)) {
        Box(Modifier.padding(horizontal = 16.dp)) { TopBar(nav, "TV en directo") }
        when (widthClass) {
            WidthClass.Compact -> Column(Modifier.weight(1f)) {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    previewBox(Modifier.fillMaxWidth())
                    NowLine(preview, programs.firstOrNull(), fmt)
                }
                Gap(height = 10.dp)
                categoryChips()
                qualityChips()
                channelList(Modifier.weight(1f))
            }
            WidthClass.Medium -> Row(Modifier.weight(1f).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(0.4f).fillMaxHeight()) {
                    categoryChips()
                    qualityChips()
                    channelList(Modifier.weight(1f))
                }
                Column(Modifier.weight(0.6f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    previewBox(Modifier.fillMaxWidth())
                    ProgramPanel(preview, programs, fmt)
                }
            }
            WidthClass.Expanded -> Row(Modifier.weight(1f).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CategoryColumn(categories, selected, onSelect = ::select, onMenu = { if (it.id !in listOf(CAT_ALL, CAT_FAVORITES, CAT_RECENT)) categoryMenu = it }, modifier = Modifier.width(220.dp).fillMaxHeight())
                Column(Modifier.width(360.dp).fillMaxHeight()) {
                    qualityChips()
                    channelList(Modifier.weight(1f))
                }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    previewBox(Modifier.fillMaxWidth())
                    ProgramPanel(preview, programs, fmt)
                }
            }
        }
    }

    pinFor?.let { cat ->
        PinSheet(
            title = "Categoría bloqueada",
            message = "Introduce el PIN parental para ver «${cat.name}».",
            onSubmit = { pin -> graph.parental.verify(pin).also { ok -> if (ok) vm.selectedCategory.value = cat.id } },
            onDismiss = { pinFor = null },
        )
    }
    categoryMenu?.let { cat ->
        val locked = graph.parental.isMarkedLocked(ContentKind.LIVE.name, cat.id)
        OptionsSheet(
            title = cat.name,
            options = listOf(
                SheetOption(
                    if (locked) "Desbloquear categoría" else "Bloquear categoría (control parental)",
                    if (locked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                    destructive = !locked,
                ) {
                    if (!graph.parental.hasPin()) nav.go(Routes.SETTINGS)
                    else scope.launch { graph.parental.toggleLock(ContentKind.LIVE.name, cat.id) }
                },
            ),
            onDismiss = { categoryMenu = null },
        )
    }
    channelMenu?.let { ch ->
        val isFav = ch.streamId in favorites
        val now by produceState<Program?>(null, ch.streamId) {
            value = playlist?.let { p -> runCatching { graph.epg.nowAndNext(p, ch, 1).firstOrNull() }.getOrNull() }
        }
        OptionsSheet(
            title = ch.name,
            subtitle = now?.let { "${fmt.range(it.startAt, it.stopAt)} · ${it.title}" },
            options = buildList {
                add(SheetOption(if (isFav) "Quitar de favoritos" else "Añadir a favoritos", if (isFav) Icons.Rounded.FavoriteBorder else Icons.Rounded.Favorite) {
                    playlist?.let { p -> scope.launch { graph.library.toggleFavorite(p.id, ContentKind.LIVE, ch.streamId) } }
                })
                add(SheetOption("Ver a pantalla completa", Icons.Rounded.Fullscreen) {
                    val index = channels.indexOfFirst { it.streamId == ch.streamId }
                    if (index >= 0) watch(channels, index) else watch(listOf(ch), 0)
                })
                if (ch.tvArchive) add(SheetOption("Catch-up (ver lo emitido)", Icons.Rounded.History, detail = "Programas pasados") {
                    nav.go(catchupRoute(ch.streamId))
                })
            },
            onDismiss = { channelMenu = null },
        )
    }
}

/** Previsualización 16:9: el vídeo debajo y el toque encima (nunca el vídeo dentro de la superficie). */
@Composable
private fun PreviewVideo(player: Player, channel: ChannelEntity?, loading: Boolean, failed: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(modifier.aspectRatio(16f / 9f).clip(RoundedCornerShape(LumenDimens.radiusMedium)).background(Color.Black)) {
        if (channel != null) VideoSurface(player, Modifier.fillMaxSize())
        when {
            channel == null -> Unit
            failed -> LText("No disponible", style = Lumen.type.callout, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.Center))
            loading -> LoadingSpinner(Modifier.align(Alignment.Center))
        }
        TapSurface(onClick = onClick, modifier = Modifier.fillMaxSize(), radius = LumenDimens.radiusMedium, color = Color.Transparent) {
            if (channel != null) {
                LText(
                    channel.name,
                    style = Lumen.type.callout,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                        .clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Icon(
                    Icons.Rounded.Fullscreen, contentDescription = "Pantalla completa", tint = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                        .clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f)).padding(6.dp).size(20.dp),
                )
            }
        }
    }
}

/** Debajo de la previsualización en vertical: qué se emite ahora en el canal que suena. */
@Composable
private fun NowLine(channel: ChannelEntity?, now: Program?, fmt: TimeFormatter) {
    val c = Lumen.colors
    if (channel == null) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (now != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LText(fmt.range(now.startAt, now.stopAt), style = Lumen.type.callout, color = c.accent)
                LText(now.title, style = Lumen.type.callout, maxLines = 1, modifier = Modifier.weight(1f))
            }
            ProgressLine(now.progress())
        } else {
            LText("Sin información de programación", style = Lumen.type.callout, color = c.textTertiary)
        }
    }
}

/** EPG del canal que suena: ahora (con progreso y sinopsis) y a continuación. */
@Composable
private fun ProgramPanel(channel: ChannelEntity?, programs: List<Program>, fmt: TimeFormatter) {
    val c = Lumen.colors
    if (channel == null) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LText(channel.name, style = Lumen.type.headline, maxLines = 1)
        if (programs.isEmpty()) LText("Sin información de programación", style = Lumen.type.body, color = c.textSecondary)
        programs.forEachIndexed { i, prog ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LText(fmt.range(prog.startAt, prog.stopAt), style = Lumen.type.callout, color = if (i == 0) c.accent else c.textSecondary)
                    LText(prog.title, style = Lumen.type.callout, maxLines = 1)
                }
                if (i == 0) {
                    ProgressLine(prog.progress())
                    if (prog.description.isNotBlank()) LText(prog.description, style = Lumen.type.body, color = c.textSecondary, maxLines = 3)
                    Gap(height = 4.dp)
                    LText("A CONTINUACIÓN", style = Lumen.type.caption, color = c.textTertiary)
                }
            }
        }
    }
}

@Composable
private fun CategoryChips(categories: List<CategoryItem>, selected: String, onSelect: (CategoryItem) -> Unit, onMenu: (CategoryItem) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        items(categories, key = { it.id }) { cat ->
            CategoryChip(cat, cat.id == selected, { onSelect(cat) }, { onMenu(cat) })
        }
    }
}

@Composable
private fun CategoryChip(cat: CategoryItem, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = Lumen.colors
    TapSurface(onClick = onClick, onLongClick = onLongClick, radius = 50.dp, color = if (selected) c.accent else c.surface, modifier = Modifier.heightIn(min = 40.dp)) {
        Row(Modifier.align(Alignment.Center).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (cat.locked) Icon(Icons.Rounded.Lock, contentDescription = "Bloqueada", tint = if (selected) c.onAccent else c.textSecondary, modifier = Modifier.size(14.dp))
            LText(cat.name, style = Lumen.type.callout, color = if (selected) c.onAccent else c.textPrimary, maxLines = 1)
            cat.count?.let { LText("$it", style = Lumen.type.caption, color = if (selected) c.onAccent.copy(alpha = 0.7f) else c.textTertiary) }
        }
    }
}

/** Tableta: categorías en columna, como la TV. */
@Composable
private fun CategoryColumn(categories: List<CategoryItem>, selected: String, onSelect: (CategoryItem) -> Unit, onMenu: (CategoryItem) -> Unit, modifier: Modifier) {
    val c = Lumen.colors
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item { LText("CATEGORÍAS", style = Lumen.type.caption, color = c.textSecondary, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)) }
        items(categories, key = { it.id }) { cat ->
            val on = cat.id == selected
            TapSurface(onClick = { onSelect(cat) }, onLongClick = { onMenu(cat) }, radius = LumenDimens.radiusSmall, color = if (on) c.accent else Color.Transparent, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                Row(Modifier.align(Alignment.CenterStart).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LText(cat.name, style = Lumen.type.callout, color = if (on) c.onAccent else c.textPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                    if (cat.locked) Icon(Icons.Rounded.Lock, contentDescription = "Bloqueada", tint = if (on) c.onAccent else c.textSecondary, modifier = Modifier.size(14.dp))
                    cat.count?.let { LText("$it", style = Lumen.type.caption, color = if (on) c.onAccent else c.textTertiary) }
                }
            }
        }
    }
}

@Composable
private fun QualityChips(options: List<QualityFilter>, selected: QualityFilter, onSelect: (QualityFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(bottom = 6.dp),
    ) {
        items(options, key = { it.name }) { q -> FilterChip(q.label, selected = q == selected, onClick = { onSelect(q) }) }
    }
}

@Composable
private fun ChannelList(
    channels: List<ChannelEntity>,
    playlist: PlaylistEntity?,
    favorites: Set<String>,
    previewId: String?,
    fmt: TimeFormatter,
    emptyTitle: String,
    emptyMessage: String,
    resetKey: Any,
    onTap: (List<ChannelEntity>, Int) -> Unit,
    onMenu: (ChannelEntity) -> Unit,
    modifier: Modifier,
) {
    if (channels.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { EmptyState(Icons.Rounded.Tv, emptyTitle, emptyMessage) }
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(resetKey) { listState.scrollToItem(0) }
    LazyColumn(modifier.fillMaxWidth(), state = listState, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
        itemsIndexed(channels, key = { _, ch -> ch.streamId }) { index, ch ->
            ChannelRow(
                channel = ch,
                playlist = playlist,
                isFavorite = ch.streamId in favorites,
                isPreviewing = ch.streamId == previewId,
                fmt = fmt,
                onClick = { onTap(channels, index) },
                onMenu = { onMenu(ch) },
            )
        }
    }
}

@Composable
private fun ChannelRow(
    channel: ChannelEntity,
    playlist: PlaylistEntity?,
    isFavorite: Boolean,
    isPreviewing: Boolean,
    fmt: TimeFormatter,
    onClick: () -> Unit,
    onMenu: () -> Unit,
) {
    val graph = LocalGraph.current
    val c = Lumen.colors
    val now by produceState<Program?>(null, channel.streamId, playlist?.id) {
        value = playlist?.let { p -> runCatching { graph.epg.nowAndNext(p, channel, 1).firstOrNull() }.getOrNull() }
    }
    TapSurface(
        onClick = onClick,
        onLongClick = onMenu,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        radius = 12.dp,
        color = if (isPreviewing) c.surfaceElevated else Color.Transparent,
    ) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LText(channel.num.toString(), style = Lumen.type.caption, color = c.textTertiary, modifier = Modifier.width(24.dp))
            Artwork(channel.logo, channel.name, Modifier.size(width = 52.dp, height = 34.dp), radius = 6.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isPreviewing) Icon(Icons.Rounded.PlayArrow, contentDescription = "En la previsualización", tint = c.accent, modifier = Modifier.size(16.dp))
                    LText(channel.name, style = Lumen.type.callout.copy(fontWeight = Lumen.type.headline.fontWeight), color = if (isPreviewing) c.accent else c.textPrimary, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (isFavorite) Icon(Icons.Rounded.Favorite, contentDescription = "Favorito", tint = c.accent, modifier = Modifier.size(14.dp))
                    if (channel.tvArchive) Icon(Icons.Rounded.History, contentDescription = "Catch-up", tint = c.warning, modifier = Modifier.size(14.dp))
                }
                val p = now
                if (p != null) {
                    LText("${fmt.time(p.startAt)} · ${p.title}", style = Lumen.type.caption, color = c.textSecondary, maxLines = 1)
                    ProgressLine(p.progress(), height = 2.dp)
                } else {
                    LText("Sin programación", style = Lumen.type.caption, color = c.textTertiary, maxLines = 1)
                }
            }
            IconCircleButton(Icons.Rounded.MoreVert, "Opciones de ${channel.name}", onClick = onMenu, color = Color.Transparent)
        }
    }
}
