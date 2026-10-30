package app.lumentv.ui.screens.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lumentv.data.catalog.ChannelQuality
import app.lumentv.data.catalog.QualityFilter
import app.lumentv.data.db.CategoryEntity
import app.lumentv.data.db.ChannelEntity
import app.lumentv.data.db.ContentKind
import app.lumentv.data.db.PlaylistEntity
import app.lumentv.data.epg.Program
import app.lumentv.di.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

const val CAT_ALL = "__all__"
const val CAT_FAVORITES = "__fav__"
const val CAT_RECENT = "__recent__"

data class CategoryItem(val id: String, val name: String, val count: Int? = null, val locked: Boolean = false)

/** `categoryId` es null hasta que llega la primera lista. */
data class ChannelPage(val categoryId: String?, val channels: List<ChannelEntity>)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LiveViewModel(private val graph: AppGraph) : ViewModel() {

    val playlist: StateFlow<PlaylistEntity?> = graph.playlists.active.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val selectedCategory = MutableStateFlow(CAT_ALL)
    val focusedChannel = MutableStateFlow<ChannelEntity?>(null)

    val favoriteIds: StateFlow<Set<String>> = playlist.filterNotNull()
        .flatMapLatest { graph.library.favoriteIds(it.id, ContentKind.LIVE) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val categories: StateFlow<List<CategoryItem>> = combine(
        playlist.filterNotNull().flatMapLatest { graph.catalog.categories(it.id, ContentKind.LIVE) },
        playlist.filterNotNull().flatMapLatest { graph.catalog.channelCount(it.id) },
        favoriteIds,
        graph.settings.settings,
    ) { cats, total, favs, _ ->
        listOf(
            CategoryItem(CAT_ALL, "Todos", total),
            CategoryItem(CAT_FAVORITES, "Favoritos", favs.size),
            CategoryItem(CAT_RECENT, "Historial"),
        ) + cats.map { c: CategoryEntity ->
            CategoryItem(c.categoryId, c.name, locked = graph.parental.isMarkedLocked(ContentKind.LIVE.name, c.categoryId))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val qualityFilter: StateFlow<QualityFilter> = graph.settings.settings.map { it.liveQuality }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, QualityFilter.ALL)

    private val unfilteredPage: StateFlow<ChannelPage> = combine(playlist.filterNotNull(), selectedCategory) { p, cat -> p to cat }
        .flatMapLatest { (p, cat) ->
            when (cat) {
                CAT_ALL -> graph.catalog.channels(p.id, null)
                CAT_FAVORITES -> favoriteIds.mapLatest { ids -> graph.catalog.channelsByIds(p.id, ids.toList()).sortedBy { it.sortOrder } }
                CAT_RECENT -> graph.library.history(p.id, ContentKind.LIVE, 50).mapLatest { h ->
                    val byId = graph.catalog.channelsByIds(p.id, h.map { it.itemId }).associateBy { it.streamId }
                    h.mapNotNull { byId[it.itemId] }
                }
                else -> graph.catalog.channels(p.id, cat)
            }.map { ChannelPage(cat, it) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ChannelPage(null, emptyList()))

    /** Calidades presentes en la categoría actual. Favoritos e historial no se filtran: los eligió el usuario. */
    val availableQualities: StateFlow<List<QualityFilter>> = unfilteredPage
        .map { page -> if (page.categoryId.isQualityFiltered()) ChannelQuality.available(page.channels.map { it.name }) else emptyList() }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Canales junto a la categoría a la que pertenecen: la UI sabe si la lista ya es la de la categoría elegida. */
    val channelPage: StateFlow<ChannelPage> = combine(unfilteredPage, qualityFilter) { page, q ->
        if (q == QualityFilter.ALL || !page.categoryId.isQualityFiltered()) page
        else page.copy(channels = page.channels.filter { ChannelQuality.matches(it.name, q) })
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ChannelPage(null, emptyList()))

    fun setQuality(q: QualityFilter) {
        viewModelScope.launch { graph.settings.setLiveQuality(q) }
    }

    private fun String?.isQualityFiltered() = this != null && this != CAT_FAVORITES && this != CAT_RECENT

    val channels: StateFlow<List<ChannelEntity>> = channelPage.map { it.channels }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** EPG del canal enfocado (con debounce para no saturar al hacer scroll rápido). */
    val focusedPrograms: StateFlow<List<Program>> = combine(playlist.filterNotNull(), focusedChannel.debounce(350)) { p, ch -> p to ch }
        .mapLatest { (p, ch) -> if (ch == null) emptyList() else graph.epg.nowAndNext(p, ch, 5) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Canal que suena en la previsualización. No sigue al foco: moverse por canales o categorías no
     * lo cambia; solo OK sobre un canal ([preview]). Al entrar no suena nada hasta el primer OK.
     */
    private val _previewChannel = MutableStateFlow<ChannelEntity?>(null)
    val previewChannel: StateFlow<ChannelEntity?> = _previewChannel

    fun onChannelFocused(ch: ChannelEntity) {
        focusedChannel.value = ch
    }

    fun preview(ch: ChannelEntity) {
        _previewChannel.value = ch
    }

    /** Enfoca y previsualiza un canal a la vez (al volver de pantalla completa con él sonando). */
    fun showNow(ch: ChannelEntity) {
        focusedChannel.value = ch
        _previewChannel.value = ch
    }

    fun isCategoryLocked(id: String) = graph.parental.isLocked(ContentKind.LIVE.name, id)
}
