package app.lumentv.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.lumentv.di.AppGraph
import app.lumentv.ui.components.LText
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.MainTabs
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.navigation.Routes
import app.lumentv.ui.screens.catchup.CatchupScreen
import app.lumentv.ui.screens.guide.GuideScreen
import app.lumentv.ui.screens.home.HomeScreen
import app.lumentv.ui.screens.live.LiveScreen
import app.lumentv.ui.screens.multiview.MultiViewScreen
import app.lumentv.ui.screens.player.PlayerScreen
import app.lumentv.ui.screens.playlist.PlaylistEditScreen
import app.lumentv.ui.screens.remote.RemoteSetupScreen
import app.lumentv.ui.screens.search.SearchScreen
import app.lumentv.ui.screens.settings.SettingsScreen
import app.lumentv.ui.screens.vod.MovieDetailScreen
import app.lumentv.ui.screens.vod.MoviesScreen
import app.lumentv.ui.screens.vod.SeriesDetailScreen
import app.lumentv.ui.screens.vod.SeriesScreen
import app.lumentv.ui.screens.vpn.VpnScreen
import app.lumentv.ui.theme.Lumen
import app.lumentv.ui.theme.LumenDimens
import app.lumentv.ui.theme.LumenTheme
import kotlinx.coroutines.delay

@Composable
fun LumenRoot(graph: AppGraph) {
    CompositionLocalProvider(LocalGraph provides graph) {
        LumenTheme {
            // ¿Hay alguna lista? Si no, empezamos en el alta de lista (onboarding).
            val hasPlaylist by produceState<Boolean?>(null) { value = graph.playlists.activeNow() != null }
            Box(Modifier.fillMaxSize().background(Lumen.colors.background)) {
                when (hasPlaylist) {
                    null -> LText("IPTV", style = Lumen.type.largeTitle, color = Lumen.colors.textSecondary, modifier = Modifier.align(Alignment.Center))
                    else -> LumenNavHost(startOnboarding = hasPlaylist == false)
                }
            }
        }
    }
}

@Composable
private fun LumenNavHost(startOnboarding: Boolean) {
    val controller = rememberNavController()
    val activity = LocalContext.current as? Activity
    val nav = remember(controller) { NavigatorImpl(controller) { activity?.finish() } }

    // En la pantalla raíz, Atrás no cierra a la primera: avisa y solo sale si se repite en 1,5 s.
    val entry by controller.currentBackStackEntryAsState()
    val atRoot = entry != null && controller.previousBackStackEntry == null
    var exitArmed by remember { mutableStateOf(false) }
    LaunchedEffect(exitArmed) {
        if (exitArmed) {
            delay(EXIT_CONFIRM_MS)
            exitArmed = false
        }
    }
    BackHandler(enabled = atRoot) { if (exitArmed) activity?.finish() else exitArmed = true }

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = controller, startDestination = if (startOnboarding) "onboarding" else Routes.HOME) {
            composable("onboarding") { PlaylistEditScreen(nav, playlistId = null, isOnboarding = true) }
            composable(Routes.HOME) { HomeScreen(nav) }
            composable(Routes.LIVE) { LiveScreen(nav) }
            composable(Routes.MOVIES) { MoviesScreen(nav) }
            composable(Routes.SERIES) { SeriesScreen(nav) }
            composable(Routes.GUIDE) { GuideScreen(nav) }
            composable(Routes.MOVIE_DETAIL, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                MovieDetailScreen(nav, it.arguments?.getString("id").orEmpty())
            }
            composable(Routes.SERIES_DETAIL, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                SeriesDetailScreen(nav, it.arguments?.getString("id").orEmpty())
            }
            composable(Routes.PLAYER) { PlayerScreen(nav) }
            composable(Routes.MULTIVIEW) { MultiViewScreen(nav) }
            composable(Routes.CATCHUP) { CatchupScreen(nav) }
            composable(Routes.SEARCH) { SearchScreen(nav) }
            composable(Routes.VPN) { VpnScreen(nav) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
            composable(Routes.REMOTE_SETUP) { RemoteSetupScreen(nav) }
            composable(
                Routes.PLAYLIST_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) {
                PlaylistEditScreen(nav, it.arguments?.getString("id")?.toLongOrNull(), isOnboarding = false)
            }
        }
        ExitHint(visible = exitArmed, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

private const val EXIT_CONFIRM_MS = 1_500L

@Composable
private fun ExitHint(visible: Boolean, modifier: Modifier = Modifier) {
    val c = Lumen.colors
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier.padding(bottom = LumenDimens.safeVertical + 24.dp)) {
        LText(
            "PULSA DE NUEVO PARA SALIR",
            style = Lumen.type.headline.copy(letterSpacing = 0.06.em),
            color = c.textPrimary,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(c.surfaceElevated)
                .border(1.dp, c.hairline, RoundedCornerShape(50))
                .padding(horizontal = 28.dp, vertical = 14.dp),
        )
    }
}

private class NavigatorImpl(private val controller: NavHostController, private val onExit: () -> Unit) : Navigator {
    private val tabRoutes = MainTabs.map { it.first }.toSet()

    override fun go(route: String) {
        controller.navigate(route) { launchSingleTop = true }
    }

    override fun back() {
        if (!controller.popBackStack()) onExit()
    }

    override val previousRoute: String?
        get() = controller.previousBackStackEntry?.destination?.route

    /** Cambia de pestaña principal manteniendo Inicio como raíz (Atrás desde una pestaña vuelve a Inicio). */
    override fun replaceRoot(route: String) {
        if (route == Routes.HOME || route !in tabRoutes) {
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
