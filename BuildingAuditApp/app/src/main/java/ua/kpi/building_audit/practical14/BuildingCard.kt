package ua.kpi.building_audit.practical14

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ua.kpi.building_audit.domain.BuildingAssessment
import ua.kpi.building_audit.ui.EnergyClassBadge
import ua.kpi.building_audit.ui.InfoRow
import ua.kpi.building_audit.ui.format

/**
 * Картка будівлі для списків ПЗ 14–16. Необов'язкові параметри вмикають прапорець вибору,
 * відстань і кнопку переходу до деталей, тому один компонент обслуговує три екрани.
 */
@Composable
fun BuildingCard(
    item: BuildingAssessment,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    onToggleSelected: (() -> Unit)? = null,
    distanceKm: Double? = null,
    onOpenDetails: (() -> Unit)? = null
) {
    val building = item.building
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected != null && onToggleSelected != null) Checkbox(checked = selected, onCheckedChange = { onToggleSelected() })
                Text(building.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                EnergyClassBadge(item.energyClass)
            }
            Text("${building.type.title} · ${building.address}", style = MaterialTheme.typography.bodyMedium)
            InfoRow("Розрахункові тепловтрати", "${item.metrics.designHeatLossKw.format(1)} кВт")
            InfoRow("Статус", building.status.title)
            distanceKm?.let { InfoRow("Відстань", "${it.format(2)} км") }
            onOpenDetails?.let { TextButton(onClick = it, modifier = Modifier.align(Alignment.End)) { Text("Детальніше") } }
        }
    }
}
