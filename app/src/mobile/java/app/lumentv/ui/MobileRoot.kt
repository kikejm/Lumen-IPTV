package app.lumentv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.lumentv.MobileActivity
import app.lumentv.di.AppGraph
import app.lumentv.ui.adaptive.LocalWidthClass
import app.lumentv.ui.adaptive.WidthClass
import app.lumentv.ui.adaptive.currentWidthClass
import app.lumentv.ui.components.LText
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.navigation.Routes
import app.lumentv.ui.screens.catchup.CatchupScreen
import app.lumentv.ui.screens.guide.GuideScreen
import app.lumentv.ui.screens.home.HomeScreen
import app.lumentv.ui.screens.player.PlayerScreen
import app.lumentv.ui.player.LocalMiniPlayer
import app.lumentv.ui.player.MiniPlayerBar
import app.lumentv.ui.player.MiniPlayerController
import app.lumentv.ui.screens.search.SearchScreen
import app.lumentv.ui.screens.live.LiveScreen
import app.lumentv.ui.screens.vod.MovieDetailScreen
import app.lumentv.ui.screens.vod.MoviesScreen
import app.lumentv.ui.screens.vod.SeriesDetailScreen
import app.lumentv.ui.screens.vod.SeriesScreen
import app.lumentv.ui.screens.placeholder.ComingSoonScreen
import app.lumentv.ui.screens.playlist.PlaylistEditScreen
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenTheme

/** Destinos de la navegación principal (barra inferior en vertical, raíl en horizontal). */
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
    val mini = remember { MiniPlayerController() }
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

@Composable
private fun MobileNavHost(startOnboarding: Boolean) {
    val controller = rememberNavController()
    val activity = LocalContext.current as? MobileActivity
    val nav = remember(controller) { NavigatorImpl(controller) { activity?.finish() } }
    val entry by controller.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val widthClass = LocalWidthClass.current
    val showNav = route in MainRoutes

    // En Inicio, el gesto atrás sale de la app (comportamiento estándar de Android; en el móvil
    // no hace falta el «pulsa de nuevo para salir» de la TV). Lo hace el propio NavHost.
    val host: @Composable (PaddingValues) -> Unit = { insets ->
        NavHost(navController = controller, startDestination = if (startOnboarding) ONBOARDING else Routes.HOME) {
            composable(ONBOARDING) { PlaylistEditScreen(nav, insets, playlistId = null, isOnboarding = true) }
            composable(Routes.HOME) { HomeScreen(nav, insets) }
            composable(Routes.PLAYLIST_EDIT, arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                PlaylistEditScreen(nav, insets, it.arguments?.getString("id")?.toLongOrNull(), isOnboarding = false)
            }
            // Pantallas de la fase 5 del plan: de momento, un aviso.
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
            composable(Routes.MULTIVIEW) { ComingSoonScreen(nav, insets, "Multipantalla", phase = 5, detail = true) }
            composable(Routes.VPN) { ComingSoonScreen(nav, insets, "VPN", phase = 5, detail = true) }
            composable(Routes.SETTINGS) { ComingSoonScreen(nav, insets, "Ajustes", phase = 5, detail = true) }
        }
    }

    // El NavHost está siempre en la misma posición del árbol: al girar el móvil solo aparecen o
    // desaparecen la barra y el raíl a su alrededor, y las pantallas conservan su estado.
    val showRail = showNav && widthClass != WidthClass.Compact
    val showBottomBar = showNav && widthClass == WidthClass.Compact
    var sides = WindowInsetsSides.Top + WindowInsetsSides.End
    if (!showRail) sides += WindowInsetsSides.Start // el raíl ya respeta el recorte de cámara
    // Mini reproductor: sobre la barra inferior en cualquier pantalla salvo el propio reproductor.
    val mini = LocalMiniPlayer.current
    val showMini = mini.player != null && route != Routes.PLAYER
    if (!showBottomBar && !showMini) sides += WindowInsetsSides.Bottom // la barra inferior (o el mini reproductor) ya respeta la de gestos
    val insets = WindowInsets.safeDrawing.only(sides).asPaddingValues()
    Row(Modifier.fillMaxSize()) {
        if (showRail) SideRail(route, nav)
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Box(Modifier.weight(1f)) { host(insets) }
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

@Composable
private fun SideRail(route: String?, nav: Navigator) {
    val c = Lumen.colors
    NavigationRail(
        // Incluye el recorte de cámara: en horizontal suele quedar a este lado.
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
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

    override fun replaceRoot(route: String) {
        if (route == Routes.HOME || route !in MainRoutes) {
            val popped = controller.popBackStack(Routes.HOME, inclusive = false)
            if (!popped) {
                controller.navigate(Routes.HOME) {
                    popUpTo(controller.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
            if (route != Routes.HOME) go(route)
            return
        }
        controller.navigate(route) {
            popUpTo(Routes.HOME) { inclusive = false }
            launchSingleTop = true
        }
    }
}
