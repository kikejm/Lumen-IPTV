package app.lumentv.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import app.lumentv.di.AppGraph

object Routes {
    const val HOME = "home"
    const val LIVE = "live"
    const val MOVIES = "movies"
    const val SERIES = "series"
    const val GUIDE = "guide"
    const val MOVIE_DETAIL = "movie/{id}"
    const val SERIES_DETAIL = "show/{id}"
    const val PLAYER = "player"
    const val MULTIVIEW = "multiview"
    const val CATCHUP = "catchup"
    const val SEARCH = "search"
    const val VPN = "vpn"
    const val SETTINGS = "settings"
    const val PLAYLIST_EDIT = "playlist?id={id}"
    const val REMOTE_SETUP = "remote"

    fun movie(id: String) = "movie/$id"
    fun series(id: String) = "show/$id"
    fun playlistEdit(id: Long? = null) = if (id == null) "playlist" else "playlist?id=$id"
}

/** Pestañas de la barra superior. */
val MainTabs = listOf(
    Routes.HOME to "Inicio",
    Routes.LIVE to "TV en directo",
    Routes.MOVIES to "Cine",
    Routes.SERIES to "Series",
    Routes.GUIDE to "Guía",
)

val LocalGraph = staticCompositionLocalOf<AppGraph> { error("AppGraph no disponible") }

/** Navegación simplificada que las pantallas reciben en lugar del NavController. */
interface Navigator {
    fun go(route: String)
    fun back()
    fun replaceRoot(route: String)
}
