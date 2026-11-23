package ua.kpi.building_audit.practical14

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.kpi.building_audit.ui.components.BuildingsMap

/**
 * Список LazyColumn і мапа пов'язані через focusedId у ViewModel: натискання на картку
 * переводить камеру мапи, натискання на маркер прокручує список до картки.
 */
@Composable
fun MapListScreen(onOpenDetails: (Long) -> Unit, viewModel: BuildingsViewModel = viewModel(factory = BuildingsViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.focusedId) {
        val index = state.visible.indexOfFirst { it.building.id == state.focusedId }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    Column(Modifier.fillMaxSize()) {
        BuildingsMap(
            items = state.visible,
            focusedId = state.focusedId,
            onMarkerClick = viewModel::focus,
            modifier = Modifier.fillMaxWidth().weight(0.45f)
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(0.55f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(state.visible, key = { _, item -> item.building.id }) { _, item ->
                BuildingCard(
                    item = item,
                    focused = item.building.id == state.focusedId,
                    onClick = { viewModel.focus(item.building.id) },
                    onOpenDetails = { onOpenDetails(item.building.id) }
                )
            }
        }
    }
}
