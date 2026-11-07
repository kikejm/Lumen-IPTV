package app.lumentv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.lumentv.MobileActivity
import app.lumentv.data.db.ContentKind
import app.lumentv.data.db.HistoryEntity
import app.lumentv.di.AppGraph
import app.lumentv.player.PlayItem
import app.lumentv.ui.adaptive.LocalTopNav
import app.lumentv.ui.adaptive.LocalWidthClass
import app.lumentv.ui.adaptive.WidthClass
import app.lumentv.ui.adaptive.isCompactHeight
import app.lumentv.ui.adaptive.currentWidthClass
import app.lumentv.ui.components.IconCircleButton
import app.lumentv.ui.components.LText
import app.lumentv.ui.components.TapSurface
import app.lumentv.ui.components.VpnPill
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.navigation.Routes
import app.lumentv.ui.player.LocalMiniPlayer
import app.lumentv.ui.player.MiniPlayerBar
import app.lumentv.ui.player.MiniPlayerController
import app.lumentv.ui.screens.catchup.CatchupScreen
import app.lumentv.ui.screens.guide.GuideScreen
import app.lumentv.ui.screens.home.HomeScreen
import app.lumentv.ui.screens.live.LiveScreen
import app.lumentv.ui.screens.multiview.MultiViewScreen
import app.lumentv.ui.screens.player.PlayerScreen
import app.lumentv.ui.screens.playlist.PlaylistEditScreen
import app.lumentv.ui.screens.search.SearchScreen
import app.lumentv.ui.screens.settings.SPEED_TEST_ROUTE
import app.lumentv.ui.screens.settings.SettingsScreen
import app.lumentv.ui.screens.settings.SpeedTestScreen
import app.lumentv.ui.screens.vod.MovieDetailScreen
import app.lumentv.ui.screens.vod.MoviesScreen
import app.lumentv.ui.screens.vod.SeriesDetailScreen
import app.lumentv.ui.screens.vod.SeriesScreen
import app.lumentv.ui.screens.vpn.ConfImport
import app.lumentv.ui.screens.vpn.VpnScreen
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenTheme
import kotlinx.coroutines.launch

/** Destinos de la navegación principal (barra inferior en vertical, pestañas arriba en el móvil en horizontal, raíl en la tableta). */
private data class MainDestination(val route: String, val label: String, val icon: ImageVector)

private val MainDestinations = listOf(
    MainDestination(Routes.HOME, "Inicio", Icons.Rounded.Home),
    MainDestination(Routes.LIVE, "Directo", Icons.Rounded.LiveTv),
    MainDestination(Routes.MOVIES, "Cine", Icons.Rounded.Movie),
    MainDestination(Routes.SERIES, "Series", Icons.Rounded.VideoLibrary),
    MainDestination(Routes.GUIDE, "Guía", Icons.AutoMirrored.Rounded.ViewList),
)
private val MainRoutes = MainDestinations.map { it.route }.toSet()

private const val ONBOARDING = "onboarding"

/** Catch-up con el canal preseleccionado (desde el menú de un canal). */
private const val CATCHUP_WITH_CHANNEL = "${Routes.CATCHUP}?ch={ch}"
fun catchupRoute(channelId: String) = "${Routes.CATCHUP}?ch=$channelId"

@Composable
fun MobileRoot(graph: AppGraph) {
    val mini = remember { MiniPlayerController { player, item -> saveMiniProgress(graph, player, item) } }
    CompositionLocalProvider(LocalGraph provides graph, LocalWidthClass provides currentWidthClass(), LocalMiniPlayer provides mini) {
        LumenTheme {
            // ¿Hay alguna lista? Si no, se empieza en el alta de lista (onboarding).
            val hasPlaylist by produceState<Boolean?>(null) { value = graph.playlists.activeNow() != null }
            Box(Modifier.fillMaxSize().background(Lumen.colors.background)) {
                when (hasPlaylist) {
                    null -> LText("IPTV", style = Lumen.type.largeTitle, color = Lumen.colors.textSecondary, modifier = Modifier.align(Alignment.Center))
                    else -> MobileNavHost(startOnboarding = hasPlaylist == false)
                }
            }
        }
    }
}

/**
 * Al cerrar el mini reproductor (o pararlo otro stream) se apunta el minuto de la película o el
 * episodio: si no, al reanudar volvía al punto en que se salió de pantalla completa.
 */
private fun saveMiniProgress(graph: AppGraph, player: ExoPlayer, item: PlayItem) {
    if (item.kind == ContentKind.LIVE) return
    val duration = player.duration
    if (duration == C.TIME_UNSET || duration <= 0) return
    val position = player.currentPosition.coerceAtLeast(0)
    graph.appScope.launch {
        val p = graph.playlists.activeNow() ?: return@launch
        graph.library.record(
            HistoryEntity(
                playlistId = p.id,
                kind = item.kind,
                itemId = item.itemId,
                title = item.title,
                subtitle = item.subtitle,
                image = item.image,
                positionMs = position,
                durationMs = duration,
                extra = item.extra,
            ),
        )
    }
}

@Composable
private fun MobileNavHost(startOnboarding: Boolean) {
    val controller = rememberNavController()
    val activity = LocalContext.current as? MobileActivity
    val nav = remember(controller) { NavigatorImpl(controller) { activity?.finish() } }
    val entry by controller.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val widthClass = LocalWidthClass.current
    val showNav = route in MainRoutes

    // Un .conf abierto o compartido desde otra app se importa en MobileActivity; aquí se lleva al
    // usuario a la pantalla VPN, que muestra el resultado. Las importaciones hechas desde la propia
    // pantalla VPN no navegan: si terminan cuando el usuario ya se ha ido, el aviso espera a que vuelva.
    val confNotice by ConfImport.notice.collectAsState()
    LaunchedEffect(confNotice, route == null) {
        if (confNotice?.fromOtherApp == true && route != null && route != Routes.VPN) nav.go(Routes.VPN)
    }

    // Sin ninguna lista (se ha borrado la última en Ajustes) se vuelve al alta de lista.
    val graph = LocalGraph.current
    val mini = LocalMiniPlayer.current
    val playlists by graph.playlists.playlists.collectAsState(initial = null)
    LaunchedEffect(playlists?.isEmpty(), route == null) {
        if (playlists?.isEmpty() == true && route != null && route != ONBOARDING) {
            mini.close()
            controller.navigate(ONBOARDING) { popUpTo(controller.graph.id) { inclusive = true } }
        }
    }

    // En Inicio, Atrás pregunta antes de salir de la app (HomeScreen).
    val host: @Composable (PaddingValues) -> Unit = { insets ->
        NavHost(navController = controller, startDestination = if (startOnboarding) ONBOARDING else Routes.HOME) {
            composable(ONBOARDING) { PlaylistEditScreen(nav, insets, playlistId = null, isOnboarding = true) }
            composable(Routes.HOME) { HomeScreen(nav, insets) }
            composable(Routes.PLAYLIST_EDIT, arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                PlaylistEditScreen(nav, insets, it.arguments?.getString("id")?.toLongOrNull(), isOnboarding = false)
            }
            composable(Routes.LIVE) { LiveScreen(nav, insets) }
            composable(Routes.MOVIES) { MoviesScreen(nav, insets) }
            composable(Routes.SERIES) { SeriesScreen(nav, insets) }
            composable(Routes.GUIDE) { GuideScreen(nav, insets) }
            composable(Routes.SEARCH) { SearchScreen(nav, insets) }
            // «catchup» a secas también entra aquí (el parámetro es opcional).
            composable(CATCHUP_WITH_CHANNEL, arguments = listOf(navArgument("ch") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                CatchupScreen(nav, insets, it.arguments?.getString("ch"))
            }
            composable(Routes.MOVIE_DETAIL, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                MovieDetailScreen(nav, insets, it.arguments?.getString("id").orEmpty())
            }
            composable(Routes.SERIES_DETAIL, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                SeriesDetailScreen(nav, insets, it.arguments?.getString("id").orEmpty())
            }
            composable(Routes.PLAYER) { PlayerScreen(nav, insets) }
            composable(Routes.MULTIVIEW) { MultiViewScreen(nav, insets) }
            composable(Routes.VPN) { VpnScreen(nav, insets) }
            composable(Routes.SETTINGS) { SettingsScreen(nav, insets) }
            composable(SPEED_TEST_ROUTE) { SpeedTestScreen(nav, insets) }
        }
    }

    // El NavHost está siempre en la misma posición del árbol: al girar el móvil solo aparecen o
    // desaparecen las barras y el raíl a su alrededor, y las pantallas conservan su estado.
    // Móvil en horizontal (altura compacta): pestañas arriba en vez del raíl, que se comía 80 dp de
    // ancho y en ≈ 340 dp de alto no dejaba sitio a su cabecera. Tableta y plegable siguen con raíl.
    val showTopTabs = showNav && widthClass != WidthClass.Compact && isCompactHeight()
    val showRail = showNav && widthClass != WidthClass.Compact && !showTopTabs
    val showBottomBar = showNav && widthClass == WidthClass.Compact
    var sides: WindowInsetsSides = WindowInsetsSides.End
    if (!showTopTabs) sides += WindowInsetsSides.Top // la barra de pestañas ya respeta la de estado
    if (!showRail) sides += WindowInsetsSides.Start // el raíl ya respeta el recorte de cámara
    // Con el teclado abierto el mini reproductor se oculta: quedaría tapado y su hueco se sumaría
    // al del teclado en las pantallas con campos de texto.
    // (derivedStateOf: solo recompone al abrirse o cerrarse, no en cada fotograma de la animación).
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val imeOpen by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
    // Mini reproductor: sobre la barra inferior en cualquier pantalla salvo el propio reproductor.
    val showMini = mini.player != null && route != Routes.PLAYER && !imeOpen
    if (!showBottomBar && !showMini) sides += WindowInsetsSides.Bottom // la barra inferior (o el mini reproductor) ya respeta la de gestos
    // Sin el teclado (safeDrawing lo incluye): lo aplican con imePadding solo las pantallas con campos
    // de texto. Si estuviera aquí también, el hueco sería el doble y taparía el formulario.
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(sides).asPaddingValues()
    Row(Modifier.fillMaxSize()) {
        if (showRail) SideRail(route, nav)
        Column(Modifier.weight(1f).fillMaxHeight()) {
            if (showTopTabs) TopTabsBar(route, nav)
            Box(Modifier.weight(1f)) {
                CompositionLocalProvider(LocalTopNav provides showTopTabs) { host(insets) }
            }
            if (showMini) {
                MiniPlayerBar(
                    mini,
                    onOpen = { nav.go(Routes.PLAYER) },
                    modifier = if (showBottomBar) Modifier else Modifier.navigationBarsPadding(),
                )
            }
            if (showBottomBar) BottomBar(route, nav)
        }
    }
}

@Composable
private fun BottomBar(route: String?, nav: Navigator) {
    val c = Lumen.colors
    NavigationBar(containerColor = c.surface, contentColor = c.textPrimary) {
        MainDestinations.forEach { d ->
            NavigationBarItem(
                selected = route == d.route,
                onClick = { if (route != d.route) nav.replaceRoot(d.route) },
                icon = { Icon(d.icon, contentDescription = null) },
                label = { Text(d.label, style = Lumen.type.caption) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = c.accent,
                    selectedTextColor = c.accent,
                    indicatorColor = c.surfaceElevated,
                    unselectedIconColor = c.textSecondary,
                    unselectedTextColor = c.textSecondary,
                ),
            )
        }
    }
}

/**
 * Móvil en horizontal: la navegación principal va arriba, como en la TV (marca · pestañas en control
 * segmentado · píldora VPN · buscar · ajustes), y sustituye a la barra superior de cada pestaña
 * (`TopBar` no se pinta con [LocalTopNav]). Así el contenido usa todo el ancho y el alto solo pierde
 * lo que ya ocupaba esa barra. Si en un móvil estrecho no caben las pestañas, se desplazan.
 */
@Composable
private fun TopTabsBar(route: String?, nav: Navigator) {
    val c = Lumen.colors
    Row(
        Modifier
            .fillMaxWidth()
            // Barra de estado y recorte de cámara (en horizontal, a un lado); sin el teclado.
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LText("IPTV", style = Lumen.type.title)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(c.surface)
                    .horizontalScroll(rememberScrollState())
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                MainDestinations.forEach { d ->
                    val selected = route == d.route
                    val tint = if (selected) c.accent else c.textSecondary
                    // 40 dp a la vista; con los 4 dp del contenedor por arriba y por abajo, 48 dp de zona táctil.
                    TapSurface(
                        onClick = { if (!selected) nav.replaceRoot(d.route) },
                        radius = 50.dp,
                        color = if (selected) c.surfaceElevated else Color.Transparent,
                        modifier = Modifier.heightIn(min = 40.dp),
                    ) {
                        Row(
                            Modifier.align(Alignment.Center).padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(d.icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                            Text(d.label, style = Lumen.type.callout, color = tint, maxLines = 1)
                        }
                    }
                }
            }
        }
        VpnPill(onClick = { nav.go(Routes.VPN) }, showServer = false)
        IconCircleButton(Icons.Rounded.Search, "Buscar", onClick = { nav.go(Routes.SEARCH) })
        IconCircleButton(Icons.Rounded.Settings, "Ajustes", onClick = { nav.go(Routes.SETTINGS) })
    }
}

/** Tableta y plegable: raíl lateral con la marca arriba. */
@Composable
private fun SideRail(route: String?, nav: Navigator) {
    val c = Lumen.colors
    NavigationRail(
        // Incluye el recorte de cámara: en horizontal suele quedar a este lado. Sin el teclado.
        windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
        containerColor = c.surface,
        contentColor = c.textPrimary,
        header = { LText("IPTV", style = Lumen.type.headline, modifier = Modifier.padding(vertical = 12.dp)) },
    ) {
        MainDestinations.forEach { d ->
            NavigationRailItem(
                selected = route == d.route,
                onClick = { if (route != d.route) nav.replaceRoot(d.route) },
                icon = { Icon(d.icon, contentDescription = null) },
                label = { Text(d.label, style = Lumen.type.caption) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = c.accent,
                    selectedTextColor = c.accent,
                    indicatorColor = c.surfaceElevated,
                    unselectedIconColor = c.textSecondary,
                    unselectedTextColor = c.textSecondary,
                ),
            )
        }
    }
}

/** Mismo contrato que la TV: Inicio es la raíz y Atrás desde una pestaña vuelve a Inicio. */
private class NavigatorImpl(private val controller: NavHostController, private val onExit: () -> Unit) : Navigator {
    override fun go(route: String) {
        controller.navigate(route) { launchSingleTop = true }
    }

    override fun back() {
        if (!controller.popBackStack()) onExit()
    }

    override val previousRoute: String?
        get() = controller.previousBackStackEntry?.destination?.route

    /**
     * Pestañas (barra inferior, raíl y accesos de Inicio): como en las apps de Android, cada pestaña
     * guarda su pila y su estado (categoría, desplazamiento) al cambiar a otra y lo recupera al volver.
     */
    override fun replaceRoot(route: String) {
        val hasHome = runCatching { controller.getBackStackEntry(Routes.HOME) }.isSuccess
        if (!hasHome) {
            controller.navigate(Routes.HOME) {
                popUpTo(controller.graph.id) { inclusive = true }
                launchSingleTop = true
            }
            if (route != Routes.HOME) go(route)
            return
        }
        if (route !in MainRoutes) {
            controller.popBackStack(Routes.HOME, inclusive = false)
            go(route)
            return
        }
        // Solo se guarda el estado de una pestaña (no el de Ajustes o el alta de lista).
        val fromTab = controller.currentDestination?.route in MainRoutes
        controller.navigate(route) {
            popUpTo(Routes.HOME) { saveState = fromTab }
            launchSingleTop = true
            restoreState = true
        }
    }
}
