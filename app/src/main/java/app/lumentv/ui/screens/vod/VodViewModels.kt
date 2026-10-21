package app.lumentv.ui.screens.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lumentv.data.db.ContentKind
import app.lumentv.data.db.MovieEntity
import app.lumentv.data.db.PlaylistEntity
import app.lumentv.data.db.SeriesEntity
import app.lumentv.di.AppGraph
import app.lumentv.ui.screens.live.CAT_ALL
import app.lumentv.ui.screens.live.CAT_FAVORITES
import app.lumentv.ui.screens.live.CAT_RECENT
import app.lumentv.ui.screens.live.CategoryItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class MoviesViewModel(private val graph: AppGraph) : ViewModel() {
    val playlist: StateFlow<PlaylistEntity?> = graph.playlists.active.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val selectedCategory = MutableStateFlow(CAT_ALL)
    val focused = MutableStateFlow<MovieEntity?>(null)

    val favoriteIds = playlist.filterNotNull().flatMapLatest { graph.library.favoriteIds(it.id, ContentKind.VOD) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val categories: StateFlow<List<CategoryItem>> = combine(
        playlist.filterNotNull().flatMapLatest { graph.catalog.categories(it.id, ContentKind.VOD) },
        favoriteIds,
        graph.settings.settings,
    ) { cats, favs, _ ->
        listOf(
            CategoryItem(CAT_ALL, "Todas"),
            CategoryItem(CAT_RECENT, "Continuar viendo"),
            CategoryItem(CAT_FAVORITES, "Mi lista", favs.size),
        ) + cats.map { CategoryItem(it.categoryId, it.name, locked = graph.parental.isMarkedLocked(ContentKind.VOD.name, it.categoryId)) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val movies: StateFlow<List<MovieEntity>> = combine(playlist.filterNotNull(), selectedCategory) { p, c -> p to c }
        .flatMapLatest { (p, cat) ->
            when (cat) {
                CAT_ALL -> graph.catalog.movies(p.id, null)
                CAT_FAVORITES -> favoriteIds.mapLatest { graph.catalog.moviesByIds(p.id, it.toList()) }
                CAT_RECENT -> graph.library.history(p.id, ContentKind.VOD, 40).mapLatest { h ->
                    val byId = graph.catalog.moviesByIds(p.id, h.map { it.itemId }).associateBy { it.streamId }
                    h.mapNotNull { byId[it.itemId] }
                }
                else -> graph.catalog.movies(p.id, cat)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun isCategoryLocked(id: String) = graph.parental.isLocked(ContentKind.VOD.name, id)
}

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesViewModel(private val graph: AppGraph) : ViewModel() {
    val playlist: StateFlow<PlaylistEntity?> = graph.playlists.active.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val selectedCategory = MutableStateFlow(CAT_ALL)
    val focused = MutableStateFlow<SeriesEntity?>(null)

    val favoriteIds = playlist.filterNotNull().flatMapLatest { graph.library.favoriteIds(it.id, ContentKind.SERIES) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val categories: StateFlow<List<CategoryItem>> = combine(
        playlist.filterNotNull().flatMapLatest { graph.catalog.categories(it.id, ContentKind.SERIES) },
        favoriteIds,
        graph.settings.settings,
    ) { cats, favs, _ ->
        listOf(
            CategoryItem(CAT_ALL, "Todas"),
            CategoryItem(CAT_FAVORITES, "Mi lista", favs.size),
        ) + cats.map { CategoryItem(it.categoryId, it.name, locked = graph.parental.isMarkedLocked(ContentKind.SERIES.name, it.categoryId)) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val series: StateFlow<List<SeriesEntity>> = combine(playlist.filterNotNull(), selectedCategory) { p, c -> p to c }
        .flatMapLatest { (p, cat) ->
            when (cat) {
                CAT_ALL -> graph.catalog.series(p.id, null)
                CAT_FAVORITES -> favoriteIds.mapLatest { graph.catalog.seriesByIds(p.id, it.toList()) }
                else -> graph.catalog.series(p.id, cat)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun isCategoryLocked(id: String) = graph.parental.isLocked(ContentKind.SERIES.name, id)
}
