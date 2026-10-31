package app.lumentv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import app.lumentv.ui.onMenuKey
import app.lumentv.ui.screens.live.CategoryItem
import app.lumentv.ui.theme.Lumen

/**
 * Columna de categorías reutilizada por Directo, Cine y Series. ☰ sobre una categoría = opciones.
 * `selectedFocusRequester` se asigna a la categoría activa; la lista arranca desplazada hasta ella.
 */
@Composable
fun CategoryColumn(
    items: List<CategoryItem>,
    selectedId: String,
    onSelect: (CategoryItem) -> Unit,
    modifier: Modifier = Modifier,
    onMenu: ((CategoryItem) -> Unit)? = null,
    selectedFocusRequester: FocusRequester? = null,
) {
    val c = Lumen.colors
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = items.indexOfFirst { it.id == selectedId }.coerceAtLeast(0))
    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 6.dp),
    ) {
        items(items, key = { it.id }) { item ->
            val selected = item.id == selectedId
            FocusSurface(
                onClick = { onSelect(item) },
                modifier = Modifier.fillMaxWidth()
                    .then(if (onMenu != null) Modifier.onMenuKey { onMenu(item) } else Modifier)
                    .then(if (selected && selectedFocusRequester != null) Modifier.focusRequester(selectedFocusRequester) else Modifier),
                containerColor = if (selected) c.accent else Color.Transparent,
                focusedContainerColor = if (selected) c.accent else c.surfaceFocused,
                contentColor = if (selected) c.onAccent else c.textPrimary,
                focusedContentColor = if (selected) c.onAccent else c.textPrimary,
                focusedScale = 1.03f,
                radius = 12.dp,
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LText(item.name, style = Lumen.type.callout, maxLines = 1, modifier = Modifier.weight(1f))
                    if (item.locked) Icon(Icons.Rounded.Lock, contentDescription = "Bloqueada", modifier = Modifier.size(14.dp))
                    item.count?.let { LText(it.toString(), style = Lumen.type.caption) }
                }
            }
        }
    }
}
