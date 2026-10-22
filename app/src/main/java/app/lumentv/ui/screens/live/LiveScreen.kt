package app.lumentv.ui.screens.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Icon
import app.lumentv.core.util.TimeFormatter
import app.lumentv.data.catalog.QualityFilter
import app.lumentv.data.db.ChannelEntity
import app.lumentv.data.db.ContentKind
import app.lumentv.data.db.PlaylistEntity
import app.lumentv.player.PlayerFactory
import app.lumentv.ui.components.Artwork
import app.lumentv.ui.components.CategoryColumn
import app.lumentv.ui.components.EmptyState
import app.lumentv.ui.components.FocusSurface
import app.lumentv.ui.components.Gap
import app.lumentv.ui.components.KeyHints
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.MenuOption
import app.lumentv.ui.components.OptionsDialog
import app.lumentv.ui.components.PinDialog
import app.lumentv.ui.components.ProgressLine
import app.lumentv.ui.components.TopBar
import app.lumentv.ui.components.VideoSurface
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.navigation.Routes
import app.lumentv.ui.onMenuKey
import app.lumentv.ui.playChannels
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PANEL_ANIM_MS = 220

/** El destino puede no estar compuesto aún (columna entrando en animación, lista recién llegada): reintenta. */
private suspend fun FocusRequester.requestFocusWhenReady() {
    repeat(10) {
        if (runCatching { requestFocus() }.isSuccess) return
        delay(30)
    }
}

@Composable
fun LiveScreen(nav: Navigator) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: LiveViewModel = viewModel { LiveViewModel(graph) }
    val playlist by vm.playlist.collectAsState()
    val categories by vm.categories.collectAsState()
    val selected by vm.selectedCategory.collectAsState()
    val page by vm.channelPage.collectAsState()
    val channels = page.channels
    val quality by vm.qualityFilter.collectAsState()
    val qualities by vm.availableQualities.collectAsState()
    val favorites by vm.favoriteIds.collectAsState()
    val focused by vm.focusedChannel.collectAsState()
    val programs by vm.focusedPrograms.collectAsState()
    val preview by vm.previewChannel.collectAsState()
    val settings by graph.settings.settings.collectAsState()
    val fmt = remember(settings.use24h, settings.epgOffsetHours) { TimeFormatter(settings.use24h, settings.epgOffsetHours * 3_600_000L) }
    val c = Lumen.colors

    var pinFor by remember { mutableStateOf<CategoryItem?>(null) }
    var menuFor by remember { mutableStateOf<CategoryItem?>(null) }
    var channelMenu by remember { mutableStateOf<ChannelEntity?>(null) }

    // Reproductor de previsualización: vive mientras la pantalla está abierta.
    val player = remember { graph.players.create(lightweight = true) }
    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(preview, playlist) {
        val p = playlist ?: return@LaunchedEffect
        val ch = preview ?: return@LaunchedEffect
        player.setMediaItem(PlayerFactory.mediaItem(graph.streams.live(p, ch).url))
        player.prepare()
        player.playWhenReady = true
    }

    // Las categorías solo se muestran mientras se navega por ellas: ◀ desde los canales las abre,
    // ▶ o elegir una las cierra y el foco vuelve a los canales. Oculta, la previsualización crece.
    var showCategories by rememberSaveable { mutableStateOf(false) }
    // Tras elegir categoría, el foco espera a que llegue la lista nueva para ir a su primer canal.
    var focusFirstChannel by remember { mutableStateOf(false) }
    val categoryFocus = remember { FocusRequester() }
    // Foco inicial en un canal una vez por visita: al volver del reproductor, el que se estaba viendo.
    var initialFocusDone by remember { mutableStateOf(false) }
    val channelFocus = remember { FocusRequester() }
    val pageReady = page.categoryId == selected
    val categoriesVisible = showCategories || (pageReady && channels.isEmpty())
    val channelsWeight by animateFloatAsState(if (categoriesVisible) 1.15f else 1f, tween(PANEL_ANIM_MS), label = "channels")
    val previewWeight by animateFloatAsState(if (categoriesVisible) 1f else 1.3f, tween(PANEL_ANIM_MS), label = "preview")

    LaunchedEffect(showCategories) { if (showCategories) categoryFocus.requestFocusWhenReady() }
    LaunchedEffect(page, focusFirstChannel) {
        if (!focusFirstChannel || !pageReady) return@LaunchedEffect
        if (channels.isNotEmpty()) channelFocus.requestFocusWhenReady()
        focusFirstChannel = false
    }

    fun select(cat: CategoryItem) {
        if (vm.isCategoryLocked(cat.id)) {
            pinFor = cat
        } else {
            vm.selectedCategory.value = cat.id
            focusFirstChannel = true
        }
    }

    Column(
        Modifier.fillMaxSize().background(c.background)
            .padding(horizontal = LumenDimens.safeHorizontal, vertical = LumenDimens.safeVertical),
    ) {
        TopBar(nav, Routes.LIVE)
        Gap(height = 18.dp)
        Row(Modifier.weight(1f)) {
            // 1) Categorías (plegables)
            AnimatedVisibility(
                visible = categoriesVisible,
                enter = expandHorizontally(tween(PANEL_ANIM_MS), expandFrom = Alignment.Start) + fadeIn(tween(PANEL_ANIM_MS)),
                exit = shrinkHorizontally(tween(PANEL_ANIM_MS), shrinkTowards = Alignment.Start) + fadeOut(tween(PANEL_ANIM_MS)),
            ) {
                Row(
                    Modifier.fillMaxHeight().onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight && channels.isNotEmpty()) {
                            runCatching { channelFocus.requestFocus() }
                            true
                        } else {
                            false
                        }
                    },
                ) {
                    CategoryColumn(
                        items = categories,
                        selectedId = selected,
                        onSelect = ::select,
                        onMenu = { if (it.id !in listOf(CAT_ALL, CAT_FAVORITES, CAT_RECENT)) menuFor = it },
                        selectedFocusRequester = categoryFocus,
                        modifier = Modifier.width(190.dp).fillMaxHeight(),
                    )
                }
            }
            // Separador siempre visible: plegado, recuerda que ◀ despliega las categorías.
            PanelHandle(expanded = categoriesVisible)

            // 2) Canales (con filtro de calidad encima si la categoría tiene varias)
            Column(Modifier.weight(channelsWeight).fillMaxHeight()) {
                if (qualities.size > 1 || quality != QualityFilter.ALL) {
                    QualityRow(
                        options = if (quality in qualities) qualities else qualities + quality,
                        selected = quality,
                        onSelect = vm::setQuality,
                        onFocus = { showCategories = false },
                    )
                }
                Box(
                    Modifier.weight(1f).fillMaxWidth().onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) {
                            showCategories = true
                            true
                        } else {
                            false
                        }
                    },
                ) {
                    if (channels.isEmpty()) {
                        val filtered = quality != QualityFilter.ALL && selected != CAT_FAVORITES && selected != CAT_RECENT
                        EmptyState(
                            Icons.Rounded.Tv,
                            when {
                                selected == CAT_FAVORITES -> "Sin favoritos"
                                selected == CAT_RECENT -> "Sin historial"
                                filtered -> "Ningún canal ${quality.label}"
                                else -> "Sin canales"
                            },
                            when {
                                selected == CAT_FAVORITES -> "Pulsa ☰ sobre un canal para añadirlo a favoritos."
                                filtered -> "Esta categoría no tiene canales en esa calidad. Elige «Todas» arriba."
                                else -> "Actualiza la lista desde Inicio si acabas de añadirla."
                            },
                        )
                    } else {
                        val listState = rememberLazyListState()
                        LaunchedEffect(channels.isNotEmpty()) {
                            if (!initialFocusDone && channels.isNotEmpty()) {
                                delay(150)
                                initialFocusDone = runCatching { channelFocus.requestFocus() }.isSuccess
                            }
                        }
                        LaunchedEffect(selected, quality) { listState.scrollToItem(0) }
                        // ▶ desde las categorías vuelve al último canal enfocado; con categoría nueva, al primero.
                        val focusTarget = focused?.streamId?.takeIf { id -> !focusFirstChannel && channels.any { it.streamId == id } }
                            ?: channels.first().streamId
                        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(6.dp)) {
                            itemsIndexed(channels, key = { _, ch -> ch.streamId }) { index, ch ->
                                ChannelRow(
                                    modifier = if (ch.streamId == focusTarget) Modifier.focusRequester(channelFocus) else Modifier,
                                    channel = ch,
                                    playlist = playlist,
                                    isFavorite = ch.streamId in favorites,
                                    fmt = fmt,
                                    onFocus = {
                                        vm.focusedChannel.value = ch
                                        showCategories = false
                                    },
                                    onClick = { playlist?.let { graph.playChannels(context, nav, it, channels, index) } },
                                    onMenu = { channelMenu = ch },
                                )
                            }
                        }
                    }
                }
            }

            Gap(width = 18.dp)

            // 3) Previsualización + EPG
            Column(Modifier.weight(previewWeight).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(LumenDimens.radiusMedium)).background(Color.Black)) {
                    if (preview != null) VideoSurface(player, Modifier.fillMaxSize())
                }
                focused?.let { ch ->
                    LText(ch.name, style = Lumen.type.headline, maxLines = 1)
                    if (programs.isEmpty()) {
                        LText("Sin información de programación", style = Lumen.type.body, color = c.textSecondary)
                    }
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
        }
        Gap(height = 10.dp)
        if (categoriesVisible) {
            KeyHints("OK" to "Elegir", "☰" to "Opciones", "▶" to "Canales")
        } else {
            KeyHints("OK" to "Ver", "☰" to "Opciones", "◀" to "Categorías")
        }
    }

    pinFor?.let { cat ->
        PinDialog("Categoría bloqueada", cat.name, onDismiss = { pinFor = null }) { pin ->
            graph.parental.verify(pin).also { ok ->
                if (ok) {
                    vm.selectedCategory.value = cat.id
                    focusFirstChannel = true
                    pinFor = null
                }
            }
        }
    }
    menuFor?.let { cat ->
        val locked = graph.parental.isMarkedLocked(ContentKind.LIVE.name, cat.id)
        OptionsDialog(
            cat.name,
            listOf(
                MenuOption(if (locked) "Desbloquear categoría" else "Bloquear categoría (control parental)", if (locked) Icons.Rounded.LockOpen else Icons.Rounded.Lock) {
                    if (!graph.parental.hasPin()) nav.go(Routes.SETTINGS)
                    else scope.launch { graph.parental.toggleLock(ContentKind.LIVE.name, cat.id) }
                },
            ),
            onDismiss = { menuFor = null },
        )
    }
    channelMenu?.let { ch ->
        val isFav = ch.streamId in favorites
        OptionsDialog(
            ch.name,
            buildList {
                add(MenuOption(if (isFav) "Quitar de favoritos" else "Añadir a favoritos", if (isFav) Icons.Rounded.FavoriteBorder else Icons.Rounded.Favorite) {
                    playlist?.let { p -> scope.launch { graph.library.toggleFavorite(p.id, ContentKind.LIVE, ch.streamId) } }
                })
                if (ch.tvArchive) add(MenuOption("Ver programas pasados (catch-up)", Icons.Rounded.History) { nav.go(Routes.CATCHUP) })
            },
            onDismiss = { channelMenu = null },
        )
    }
}

/** Píldoras de calidad sobre la lista de canales. ▲ desde el primer canal llega aquí. */
@Composable
private fun QualityRow(options: List<QualityFilter>, selected: QualityFilter, onSelect: (QualityFilter) -> Unit, onFocus: () -> Unit) {
    val c = Lumen.colors
    // Desplazable: con las categorías abiertas la columna de canales no tiene ancho para las cinco píldoras.
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 6.dp, end = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { q ->
            val on = q == selected
            FocusSurface(
                onClick = { onSelect(q) },
                modifier = Modifier.onFocusChanged { if (it.isFocused) onFocus() },
                radius = 50.dp,
                containerColor = if (on) c.accent else c.surface,
                focusedContainerColor = if (on) c.accent else c.surfaceFocused,
                contentColor = if (on) c.onAccent else c.textSecondary,
                focusedContentColor = if (on) c.onAccent else c.textPrimary,
            ) {
                LText(q.label, style = Lumen.type.callout, maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

/** Línea vertical con asa y flecha entre categorías y canales. No es enfocable: solo es una pista visual. */
@Composable
private fun PanelHandle(expanded: Boolean) {
    val c = Lumen.colors
    Box(Modifier.width(28.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
        Box(Modifier.width(1.dp).fillMaxHeight().background(c.surfaceElevated))
        Column(
            Modifier.clip(RoundedCornerShape(50)).background(c.background).padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                if (expanded) Icons.AutoMirrored.Rounded.KeyboardArrowRight else Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = if (expanded) "▶ Canales" else "◀ Categorías",
                tint = c.textTertiary,
                modifier = Modifier.size(18.dp),
            )
            Box(Modifier.size(width = 4.dp, height = 40.dp).clip(RoundedCornerShape(50)).background(c.textTertiary))
        }
    }
}

@Composable
private fun ChannelRow(
    channel: ChannelEntity,
    playlist: PlaylistEntity?,
    isFavorite: Boolean,
    fmt: TimeFormatter,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val graph = LocalGraph.current
    val c = Lumen.colors
    val now by produceState<app.lumentv.data.epg.Program?>(null, channel.streamId, playlist?.id) {
        value = playlist?.let { p -> runCatching { graph.epg.nowAndNext(p, channel, 1).firstOrNull() }.getOrNull() }
    }
    FocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocus() }.onMenuKey(onMenu),
        containerColor = Color.Transparent,
        focusedContainerColor = c.surfaceFocused,
        focusedScale = 1.02f,
        radius = 12.dp,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LText(channel.num.toString(), style = Lumen.type.caption, color = c.textTertiary, modifier = Modifier.width(26.dp))
            Artwork(channel.logo, channel.name, Modifier.size(width = 52.dp, height = 34.dp), radius = 6.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LText(channel.name, style = Lumen.type.callout, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (isFavorite) Icon(Icons.Rounded.Favorite, contentDescription = "Favorito", tint = c.danger, modifier = Modifier.size(14.dp))
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
        }
    }
}
