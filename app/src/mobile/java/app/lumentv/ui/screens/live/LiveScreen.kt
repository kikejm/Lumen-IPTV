package app.lumentv.ui.screens.live

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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
import app.lumentv.ui.adaptive.isCompactHeight
import app.lumentv.ui.components.Artwork
import app.lumentv.ui.components.EPG_ROW_DELAY_MS
import app.lumentv.ui.components.EmptyState
import app.lumentv.ui.components.FilterChip
import app.lumentv.ui.components.Gap
import app.lumentv.ui.components.IconCircleButton
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.LoadingSpinner
import app.lumentv.ui.components.OptionsSheet
import app.lumentv.ui.components.PinSheet
import app.lumentv.ui.components.ProgressLine
import app.lumentv.ui.components.ReconnectPill
import app.lumentv.ui.components.SheetOption
import app.lumentv.ui.components.TapSurface
import app.lumentv.ui.components.TopBar
import app.lumentv.ui.components.VideoSurface
import app.lumentv.ui.components.rememberStreamReconnect
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
 * (`LiveViewModel`, `PlayerFactory.claimStream`): al entrar no suena nada; tocar un canal lo pasa a
 * la previsualización y tocar el que ya suena (o el vídeo) lo abre a pantalla completa. Pulsación
 * larga o «⋯» = opciones.
 *
 * - Vertical: primero solo la lista de categorías; al elegir una, previsualización arriba, chips de
 *   calidad y canales, con la flecha atrás (o el gesto atrás) para volver a las categorías.
 * - Móvil en horizontal: chips y lista · previsualización + EPG.
 * - Plegable: lista (40 %) · previsualización + EPG (60 %).
 * - Tableta: categorías · lista · previsualización + EPG.
 */
@Composable
fun LiveScreen(nav: Navigator, insets: PaddingValues) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val widthClass = LocalWidthClass.current
    val vm: LiveViewModel = viewModel { LiveViewModel(graph) }
    // En vertical: ¿se ven los canales de la categoría elegida (true) o la lista de categorías?
    // Sobrevive al reproductor y al cambio de pestaña, como la categoría y el desplazamiento.
    val showChannelsState = rememberSaveable { mutableStateOf(false) }
    var showChannels by showChannelsState
    // Al volver de pantalla completa llega su reproductor con el canal que sonaba (quizá tras hacer
    // zapping): se previsualiza ese canal ya, antes de leer el estado, para no recargarlo. Si la lista
    // a la vista no lo contiene, se abre su categoría.
    val returned = remember {
        graph.players.takeHandOff()?.also {
            graph.playback.current?.channel?.let { vm.returnTo(it, playing = true) }
            showChannelsState.value = true
        }
    }
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
    val player = remember { returned ?: graph.players.create() }
    var handedOff by remember { mutableStateOf(false) }
    var previewState by remember { mutableIntStateOf(player.playbackState) }
    // ¿Puede sonar ya? Al entrar no se carga ningún canal (como en la TV): solo si se vuelve de pantalla
    // completa con el canal sonando. Tocar un canal (o la previsualización de uno ya elegido) la activa.
    var armed by remember { mutableStateOf(returned != null) }
    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { previewState = state }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            if (!handedOff) player.release()
        }
    }
    // Reconexión igual que la TV y la multipantalla: 5 intentos, uno cada 3 s (también tras 20 s cargando).
    val reconnect = rememberStreamReconnect(player, isActive = { armed && !handedOff }) {
        graph.players.claimStream(player)
        player.seekToDefaultPosition()
        player.prepare()
        player.playWhenReady = true
    }
    LaunchedEffect(preview, playlist, armed) {
        val p = playlist ?: return@LaunchedEffect
        val ch = preview ?: return@LaunchedEffect
        if (!armed) return@LaunchedEffect
        reconnect.reset()
        val url = graph.streams.live(p, ch).url
        if (PlayerFactory.isLoaded(player, url)) {
            player.playWhenReady = true
            return@LaunchedEffect
        }
        // Una sola conexión de vídeo por cuenta: para cualquier otro reproductor antes de empezar.
        graph.players.claimStream(player)
        player.setMediaItem(PlayerFactory.mediaItem(url))
        player.prepare()
        player.playWhenReady = true
    }
    // La EPG que se muestra es la del canal que suena.
    LaunchedEffect(preview?.streamId) { preview?.let(vm::onChannelFocused) }

    fun watch(list: List<ChannelEntity>, index: Int) {
        val p = playlist ?: return
        val ch = list[index]
        // Si la previsualización ya muestra ese canal, su reproductor pasa a pantalla completa sin recargar.
        if (settings.externalPlayerPackage.isBlank() && preview?.streamId == ch.streamId &&
            PlayerFactory.isLoaded(player, graph.streams.live(p, ch).url)
        ) {
            handedOff = true
            graph.players.handOff(player)
        }
        graph.playChannels(context, nav, p, list, index)
    }
    fun tapChannel(list: List<ChannelEntity>, index: Int) {
        val ch = list[index]
        if (ch.streamId == preview?.streamId && armed) watch(list, index) else vm.preview(ch)
        armed = true
    }
    fun watchPreview() {
        val ch = preview ?: return
        if (!armed) { armed = true; return }
        if (reconnect.failed) { reconnect.retryNow(); return }
        val index = channels.indexOfFirst { it.streamId == ch.streamId }
        if (index >= 0) watch(channels, index) else watch(listOf(ch), 0)
    }
    fun open(id: String) {
        vm.selectedCategory.value = id
        showChannels = true
    }
    fun select(cat: CategoryItem) {
        if (vm.isCategoryLocked(cat.id)) pinFor = cat else open(cat.id)
    }
    val categoryMenuFor: (CategoryItem) -> Unit = { if (it.id !in listOf(CAT_ALL, CAT_FAVORITES, CAT_RECENT)) categoryMenu = it }

    // Móvil en horizontal (altura compacta) y en vertical, cada uno con su maquetación.
    val landscapePhone = isCompactHeight() && widthClass != WidthClass.Compact
    val portraitPhone = !landscapePhone && widthClass == WidthClass.Compact
    // En vertical, Atrás desde los canales vuelve a las categorías (y desde ellas, a Inicio).
    BackHandler(enabled = portraitPhone && showChannels) { showChannels = false }

    val previewBox: @Composable (Modifier) -> Unit = { modifier ->
        PreviewVideo(
            player = player,
            channel = preview,
            armed = armed,
            loading = previewState != Player.STATE_READY,
            attempt = reconnect.attempt,
            failed = reconnect.failed,
            onClick = ::watchPreview,
            modifier = modifier,
        )
    }
    val categoryChips: @Composable () -> Unit = {
        CategoryChips(categories, selected, onSelect = ::select, onMenu = categoryMenuFor)
    }
    val qualityChips: @Composable () -> Unit = {
        if (qualities.size > 1 || quality != QualityFilter.ALL) {
            QualityChips(if (quality in qualities) qualities else qualities + quality, quality, vm::setQuality)
        }
    }
    val channelList: @Composable (Modifier, Boolean) -> Unit = { modifier, showNumbers ->
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
            showNumbers = showNumbers,
            modifier = modifier,
        )
    }

    if (landscapePhone) {
        // Móvil en horizontal (≈ 340 dp de alto). La navegación, la VPN, buscar y ajustes van en la
        // barra de pestañas de arriba (MobileRoot), así que esta pantalla no lleva barra propia: a la
        // izquierda las categorías en píldoras y los canales; a la derecha la previsualización, tan
        // grande como quepa dejando a la vista lo que se emite y al menos LANDSCAPE_LIST_MIN a la lista.
        BoxWithConstraints(Modifier.fillMaxSize().padding(insets)) {
            val previewWidth = minOf(
                (maxHeight - LANDSCAPE_BELOW_PREVIEW) * (16f / 9f),
                maxWidth * 0.62f,
                maxWidth - LANDSCAPE_LIST_MIN - LANDSCAPE_GAP - 16.dp,
            ).coerceAtLeast(0.dp)
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight().padding(top = 4.dp)) {
                    categoryChips()
                    qualityChips()
                    // Sin el número del canal: la columna es estrecha y el nombre necesita el sitio.
                    channelList(Modifier.weight(1f), false)
                }
                Column(
                    Modifier.padding(start = LANDSCAPE_GAP, end = 16.dp).width(previewWidth).fillMaxHeight()
                        .verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    previewBox(Modifier.fillMaxWidth())
                    // El nombre del canal ya va sobre el vídeo.
                    ProgramPanel(preview, programs, fmt, showChannel = false)
                }
            }
        }
    } else Column(Modifier.fillMaxSize().padding(insets)) {
        Box(Modifier.padding(horizontal = 16.dp)) {
            if (portraitPhone && showChannels) {
                // La flecha de la izquierda (o el gesto atrás) vuelve a las categorías. Sin VPN, buscar ni
                // ajustes (están en la lista de categorías): el nombre de la categoría necesita el ancho.
                TopBar(
                    nav,
                    categories.firstOrNull { it.id == selected }?.name ?: "TV en directo",
                    showBack = true,
                    showActions = false,
                    onBack = { showChannels = false },
                )
            } else {
                TopBar(nav, "TV en directo")
            }
        }
        when (widthClass) {
            WidthClass.Compact -> Column(Modifier.weight(1f)) {
                // Previsualización arriba: siempre con los canales; sobre las categorías, solo si ya suena algo.
                if (showChannels || (armed && preview != null)) {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        previewBox(Modifier.fillMaxWidth())
                        NowLine(preview, programs.firstOrNull(), fmt)
                    }
                    Gap(height = 10.dp)
                }
                if (showChannels) {
                    qualityChips()
                    channelList(Modifier.weight(1f), true)
                } else {
                    CategoryList(categories, selected, onSelect = ::select, onMenu = categoryMenuFor, modifier = Modifier.weight(1f))
                }
            }
            WidthClass.Medium -> Row(Modifier.weight(1f).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(0.4f).fillMaxHeight()) {
                    categoryChips()
                    qualityChips()
                    channelList(Modifier.weight(1f), true)
                }
                Column(Modifier.weight(0.6f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    previewBox(Modifier.fillMaxWidth())
                    ProgramPanel(preview, programs, fmt)
                }
            }
            WidthClass.Expanded -> Row(Modifier.weight(1f).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CategoryColumn(categories, selected, onSelect = ::select, onMenu = categoryMenuFor, modifier = Modifier.width(220.dp).fillMaxHeight())
                Column(Modifier.width(360.dp).fillMaxHeight()) {
                    qualityChips()
                    channelList(Modifier.weight(1f), true)
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
            onSubmit = { pin -> graph.parental.verify(pin).also { ok -> if (ok) open(cat.id) } },
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

/** Móvil en horizontal: alto que se deja bajo la previsualización (la línea de lo que se emite). */
private val LANDSCAPE_BELOW_PREVIEW = 56.dp

/** Móvil en horizontal: ancho mínimo de la columna de canales. */
private val LANDSCAPE_LIST_MIN = 300.dp

private val LANDSCAPE_GAP = 12.dp

/** Previsualización 16:9: el vídeo debajo y el toque encima (nunca el vídeo dentro de la superficie). */
@Composable
private fun PreviewVideo(
    player: Player,
    channel: ChannelEntity?,
    armed: Boolean,
    loading: Boolean,
    attempt: Int,
    failed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier.aspectRatio(16f / 9f).clip(RoundedCornerShape(LumenDimens.radiusMedium)).background(Color.Black)) {
        if (channel != null && armed) VideoSurface(player, Modifier.fillMaxSize())
        when {
            channel == null || !armed -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                LText(if (channel == null) "Toca un canal para verlo aquí" else "Toca para ver", style = Lumen.type.callout, color = Color.White.copy(alpha = 0.7f))
            }
            failed -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                LText("No disponible", style = Lumen.type.headline, color = Color.White)
                LText("Toca para reintentar", style = Lumen.type.callout, color = Color.White.copy(alpha = 0.7f))
            }
            attempt > 0 -> ReconnectPill(attempt, Modifier.align(Alignment.Center))
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
                if (armed && !failed) Icon(
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
private fun ProgramPanel(channel: ChannelEntity?, programs: List<Program>, fmt: TimeFormatter, showChannel: Boolean = true) {
    val c = Lumen.colors
    if (channel == null) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showChannel) LText(channel.name, style = Lumen.type.headline, maxLines = 1)
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
    TapSurface(onClick = onClick, onLongClick = onLongClick, radius = 50.dp, color = if (selected) c.accent else c.surface, modifier = Modifier.minimumInteractiveComponentSize().heightIn(min = 40.dp)) {
        Row(Modifier.align(Alignment.Center).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (cat.locked) Icon(Icons.Rounded.Lock, contentDescription = "Bloqueada", tint = if (selected) c.onAccent else c.textSecondary, modifier = Modifier.size(14.dp))
            LText(cat.name, style = Lumen.type.callout, color = if (selected) c.onAccent else c.textPrimary, maxLines = 1)
            cat.count?.let { LText("$it", style = Lumen.type.caption, color = if (selected) c.onAccent.copy(alpha = 0.7f) else c.textTertiary) }
        }
    }
}

/**
 * Móvil en vertical: lo primero que se ve al entrar. Una fila por categoría (Todos, Favoritos e
 * Historial arriba); tocarla abre sus canales. Pulsación larga o «⋯» = bloquear (control parental).
 */
@Composable
private fun CategoryList(categories: List<CategoryItem>, selected: String, onSelect: (CategoryItem) -> Unit, onMenu: (CategoryItem) -> Unit, modifier: Modifier) {
    val c = Lumen.colors
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
        item { LText("CATEGORÍAS", style = Lumen.type.caption, color = c.textSecondary, modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 6.dp)) }
        items(categories, key = { it.id }) { cat ->
            val on = cat.id == selected
            val special = cat.id in listOf(CAT_ALL, CAT_FAVORITES, CAT_RECENT)
            TapSurface(
                onClick = { onSelect(cat) },
                onLongClick = if (special) null else ({ onMenu(cat) }),
                radius = 12.dp,
                color = if (on) c.surfaceElevated else Color.Transparent,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().align(Alignment.CenterStart).padding(start = 12.dp, end = if (special) 8.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LText(
                        cat.name,
                        style = Lumen.type.body.copy(fontWeight = Lumen.type.headline.fontWeight),
                        color = if (on) c.accent else c.textPrimary,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    if (cat.locked) Icon(Icons.Rounded.Lock, contentDescription = "Bloqueada", tint = c.textSecondary, modifier = Modifier.size(16.dp))
                    cat.count?.let { LText("$it", style = Lumen.type.callout, color = c.textTertiary) }
                    if (special) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(22.dp))
                    } else {
                        IconCircleButton(Icons.Rounded.MoreVert, "Opciones de ${cat.name}", onClick = { onMenu(cat) }, color = Color.Transparent)
                    }
                }
            }
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
    showNumbers: Boolean,
    modifier: Modifier,
) {
    if (channels.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { EmptyState(Icons.Rounded.Tv, emptyTitle, emptyMessage) }
        return
    }
    val listState = rememberLazyListState()
    // Solo al cambiar de categoría o calidad: al volver a la pantalla (del reproductor o de otra
    // pestaña) la lista se queda donde estaba.
    var lastKey by rememberSaveable { mutableStateOf(resetKey.toString()) }
    LaunchedEffect(resetKey) {
        if (resetKey.toString() != lastKey) {
            lastKey = resetKey.toString()
            listState.scrollToItem(0)
        }
    }
    LazyColumn(modifier.fillMaxWidth(), state = listState, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
        itemsIndexed(channels, key = { _, ch -> ch.streamId }) { index, ch ->
            ChannelRow(
                channel = ch,
                playlist = playlist,
                isFavorite = ch.streamId in favorites,
                isPreviewing = ch.streamId == previewId,
                fmt = fmt,
                showNumber = showNumbers,
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
    showNumber: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
) {
    val graph = LocalGraph.current
    val c = Lumen.colors
    val now by produceState<Program?>(null, channel.streamId, playlist?.id) {
        // Al desplazar deprisa las filas pasan de largo: solo piden la EPG las que se quedan a la vista.
        delay(EPG_ROW_DELAY_MS)
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
            if (showNumber) LText(channel.num.toString(), style = Lumen.type.caption, color = c.textTertiary, modifier = Modifier.width(24.dp))
            Artwork(channel.logo, channel.name, Modifier.size(width = 52.dp, height = 34.dp), radius = 6.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isPreviewing) Icon(Icons.Rounded.PlayArrow, contentDescription = "En la previsualización", tint = c.accent, modifier = Modifier.size(16.dp))
                    LText(channel.name, style = Lumen.type.callout.copy(fontWeight = Lumen.type.headline.fontWeight), color = if (isPreviewing) c.accent else c.textPrimary, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (isFavorite) Icon(Icons.Rounded.Favorite, contentDescription = "Favorito", tint = c.accent, modifier = Modifier.size(14.dp))
                    if (channel.tvArchive) Icon(Icons.Rounded.History, contentDescription = "Catch-up", tint = c.textSecondary, modifier = Modifier.size(14.dp))
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
