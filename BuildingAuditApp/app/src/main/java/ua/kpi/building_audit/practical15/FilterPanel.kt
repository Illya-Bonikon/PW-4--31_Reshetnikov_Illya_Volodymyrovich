package ua.kpi.building_audit.practical15

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import ua.kpi.building_audit.domain.BuildingQuery
import ua.kpi.building_audit.domain.EnergyClass
import ua.kpi.building_audit.domain.SortOrder
import ua.kpi.building_audit.domain.model.AuditStatus
import ua.kpi.building_audit.domain.model.BuildingType
import ua.kpi.building_audit.ui.format

/**
 * Панель багаторівневої фільтрації. Кожен ряд чипів — окремий рівень; рівні поєднуються через «і»,
 * значення в межах ряду — через «або».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterPanel(
    query: BuildingQuery,
    lossBounds: ClosedFloatingPointRange<Double>,
    onChange: ((BuildingQuery) -> BuildingQuery) -> Unit,
    showClasses: Boolean = true,
    sortOptions: List<SortOrder> = SortOrder.entries.filter { it != SortOrder.DISTANCE }
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ChipRow(BuildingType.entries, { it.title }, { it in query.types }) { type -> onChange { it.toggle(type) } }
        ChipRow(AuditStatus.entries, { it.title }, { it in query.statuses }) { status -> onChange { it.toggle(status) } }
        if (showClasses) ChipRow(EnergyClass.entries, { it.label }, { it in query.classes }) { energyClass -> onChange { it.toggle(energyClass) } }

        val range = query.designLossKw ?: lossBounds
        Text("Розрахункові тепловтрати: ${range.start.format()}–${range.endInclusive.format()} кВт", style = MaterialTheme.typography.bodyMedium)
        if (lossBounds.endInclusive > lossBounds.start) {
            RangeSlider(
                value = range.start.toFloat()..range.endInclusive.toFloat(),
                onValueChange = { value -> onChange { it.copy(designLossKw = value.start.toDouble()..value.endInclusive.toDouble()) } },
                valueRange = lossBounds.start.toFloat()..lossBounds.endInclusive.toFloat()
            )
        }

        ChipRow(sortOptions, { it.title }, { it == query.sort }) { order ->
            // Повторне натискання на активне сортування змінює напрямок.
            onChange { if (it.sort == order) it.copy(descending = !it.descending) else it.copy(sort = order) }
        }
        Text(if (query.descending) "Сортування: за спаданням" else "Сортування: за зростанням", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun <T> ChipRow(values: List<T>, label: (T) -> String, selected: (T) -> Boolean, onClick: (T) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(values) { value -> FilterChip(selected = selected(value), onClick = { onClick(value) }, label = { Text(label(value)) }) }
    }
}
