package app.lumentv.ui.screens.vod

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lumentv.data.db.ContentKind
import app.lumentv.data.db.SeriesEntity
import app.lumentv.ui.components.MenuOption
import app.lumentv.ui.components.OptionsDialog
import app.lumentv.ui.components.PinDialog
import app.lumentv.ui.navigation.LocalGraph
import app.lumentv.ui.navigation.Navigator
import app.lumentv.ui.navigation.Routes
import app.lumentv.ui.screens.live.CAT_ALL
import app.lumentv.ui.screens.live.CAT_FAVORITES
import app.lumentv.ui.screens.live.CategoryItem
import kotlinx.coroutines.launch

fun SeriesEntity.toPoster() = PosterItem(
    id = seriesId,
    title = name,
    image = cover,
    backdrop = backdrop,
    meta = listOf(releaseDate.take(4), genre).filter { it.isNotBlank() }.joinToString(" · "),
    plot = plot,
    rating = rating,
)

@Composable
fun SeriesScreen(nav: Navigator) {
    val graph = LocalGraph.current
    val scope = rememberCoroutineScope()
    val vm: SeriesViewModel = viewModel { SeriesViewModel(graph) }
    val playlist by vm.playlist.collectAsState()
    val categories by vm.categories.collectAsState()
    val selected by vm.selectedCategory.collectAsState()
    val series by vm.series.collectAsState()
    val favorites by vm.favoriteIds.collectAsState()
    val focused by vm.focused.collectAsState()
    val posters = remember(series) { series.map { it.toPoster() } }

    var pinFor by remember { mutableStateOf<CategoryItem?>(null) }
    var catMenu by remember { mutableStateOf<CategoryItem?>(null) }
    var itemMenu by remember { mutableStateOf<PosterItem?>(null) }

    PosterBrowser(
        nav = nav,
        route = Routes.SERIES,
        categories = categories,
        selectedCategory = selected,
        onSelectCategory = { if (vm.isCategoryLocked(it.id)) pinFor = it else vm.selectedCategory.value = it.id },
        onCategoryMenu = { if (it.id !in listOf(CAT_ALL, CAT_FAVORITES)) catMenu = it },
        items = posters,
        focused = focused?.toPoster(),
        restoreId = vm.restoreId,
        onFocus = { p ->
            vm.restoreId = p.id
            vm.focused.value = series.firstOrNull { it.seriesId == p.id }
        },
        onOpen = { nav.go(Routes.series(it.id)) },
        onItemMenu = { itemMenu = it },
        favorites = favorites,
        emptyTitle = "Sin series",
    )

    pinFor?.let { cat ->
        PinDialog("Categoría bloqueada", cat.name, onDismiss = { pinFor = null }) { pin ->
            graph.parental.verify(pin).also { if (it) { vm.selectedCategory.value = cat.id; pinFor = null } }
        }
    }
    catMenu?.let { cat ->
        val locked = graph.parental.isMarkedLocked(ContentKind.SERIES.name, cat.id)
        OptionsDialog(cat.name, listOf(
            MenuOption(if (locked) "Desbloquear categoría" else "Bloquear categoría", if (locked) Icons.Rounded.LockOpen else Icons.Rounded.Lock) {
                if (!graph.parental.hasPin()) nav.go(Routes.SETTINGS) else scope.launch { graph.parental.toggleLock(ContentKind.SERIES.name, cat.id) }
            },
        ), onDismiss = { catMenu = null })
    }
    itemMenu?.let { item ->
        val fav = item.id in favorites
        OptionsDialog(item.title, listOf(
            MenuOption(if (fav) "Quitar de Mi lista" else "Añadir a Mi lista", if (fav) Icons.Rounded.Remove else Icons.Rounded.Add) {
                playlist?.let { p -> scope.launch { graph.library.toggleFavorite(p.id, ContentKind.SERIES, item.id) } }
            },
        ), onDismiss = { itemMenu = null })
    }
}
