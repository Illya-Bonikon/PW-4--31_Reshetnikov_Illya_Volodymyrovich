package ua.kpi.building_audit.practical16

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.kpi.building_audit.domain.SortOrder
import ua.kpi.building_audit.location.LocationTracker
import ua.kpi.building_audit.practical14.BuildingCard
import ua.kpi.building_audit.practical14.BuildingsViewModel
import ua.kpi.building_audit.practical15.FilterPanel
import ua.kpi.building_audit.ui.Banner
import ua.kpi.building_audit.ui.components.BuildingsMap
import ua.kpi.building_audit.ui.theme.WarningAmber

private val RADII_KM = listOf(1.0, 2.0, 5.0)

@Composable
fun GpsMapScreen(viewModel: BuildingsViewModel = viewModel(factory = BuildingsViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) viewModel.startLocationUpdates()
    }

    // Коли позиція відома, найзручніше сортування — за відстанню; вмикається один раз.
    LaunchedEffect(state.userLocation != null) {
        if (state.userLocation != null) viewModel.updateQuery { it.copy(sort = SortOrder.DISTANCE) }
    }
    LaunchedEffect(state.focusedId) {
        val index = state.visible.indexOfFirst { it.building.id == state.focusedId }
        // +1: перший елемент списку — панель керування.
        if (index >= 0) listState.animateScrollToItem(index + 1)
    }

    Column(Modifier.fillMaxSize()) {
        BuildingsMap(
            items = state.visible,
            focusedId = state.focusedId,
            onMarkerClick = viewModel::focus,
            userLocation = state.userLocation,
            radiusKm = state.query.radiusKm,
            modifier = Modifier.fillMaxWidth().weight(0.42f)
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(0.58f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.userLocation == null) {
                        Button(onClick = { permission.launch(LocationTracker.PERMISSIONS) }) { Text("Визначити моє місцезнаходження") }
                    }
                    state.locationMessage?.let { Banner(it, WarningAmber) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = state.query.radiusKm == null, onClick = { viewModel.updateQuery { it.copy(radiusKm = null) } }, label = { Text("Усі") })
                        RADII_KM.forEach { radius ->
                            FilterChip(
                                selected = state.query.radiusKm == radius,
                                enabled = state.userLocation != null,
                                onClick = { viewModel.updateQuery { it.copy(radiusKm = radius) } },
                                label = { Text("≤ ${radius.toInt()} км") }
                            )
                        }
                    }
                    FilterPanel(state.query, state.lossBounds, viewModel::updateQuery, showClasses = false, sortOptions = SortOrder.entries)
                }
            }
            items(state.visible, key = { it.building.id }) { item ->
                BuildingCard(
                    item = item,
                    focused = item.building.id == state.focusedId,
                    onClick = { viewModel.focus(item.building.id) },
                    distanceKm = state.query.distanceKm(item)
                )
            }
        }
    }
}
