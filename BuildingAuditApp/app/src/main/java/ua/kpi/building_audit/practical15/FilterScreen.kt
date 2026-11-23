package ua.kpi.building_audit.practical15

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.kpi.building_audit.practical14.BuildingCard
import ua.kpi.building_audit.practical14.BuildingsViewModel
import ua.kpi.building_audit.ui.InfoRow
import ua.kpi.building_audit.ui.SectionCard
import ua.kpi.building_audit.ui.format

@Composable
fun FilterScreen(viewModel: BuildingsViewModel = viewModel(factory = BuildingsViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedTextField(
                value = state.query.text,
                onValueChange = { text -> viewModel.updateQuery { it.copy(text = text) } },
                label = { Text("Пошук за назвою або адресою") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { FilterPanel(state.query, state.lossBounds, viewModel::updateQuery) }
        item {
            SectionCard("Знайдено ${state.visible.size} з ${state.all.size}; позначено ${state.selectedIds.size}") {
                val summary = state.selectionSummary
                if (summary.buildingCount > 0) {
                    InfoRow("Опалювана площа позначених", "${summary.heatedAreaM2.format()} м²")
                    InfoRow("Вартість опалення позначених", "${summary.annualCostUah.format()} грн/рік")
                    OutlinedButton(onClick = viewModel::clearSelection) { Text("Зняти позначки") }
                } else {
                    Text("Позначте будівлі прапорцями, щоб отримати зведення")
                }
            }
        }
        items(state.visible, key = { it.building.id }) { item ->
            BuildingCard(
                item = item,
                focused = item.building.id == state.focusedId,
                onClick = { viewModel.focus(item.building.id) },
                selected = item.building.id in state.selectedIds,
                onToggleSelected = { viewModel.toggleSelection(item.building.id) }
            )
        }
    }
}
