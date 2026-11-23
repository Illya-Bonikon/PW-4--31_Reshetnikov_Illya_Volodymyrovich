package ua.kpi.building_audit.practical14

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.kpi.building_audit.domain.EnergyCalculator
import ua.kpi.building_audit.ui.EnergyClassBadge
import ua.kpi.building_audit.ui.InfoRow
import ua.kpi.building_audit.ui.SectionCard
import ua.kpi.building_audit.ui.format

@Composable
fun BuildingDetailScreen(buildingId: Long, onBack: () -> Unit, viewModel: BuildingsViewModel = viewModel(factory = BuildingsViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val item = state.all.firstOrNull { it.building.id == buildingId }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onBack) { Text("До списку") }
        if (item == null) {
            Text("Будівлю не знайдено в кеші")
            return@Column
        }
        val building = item.building
        Text(building.name, style = MaterialTheme.typography.titleLarge)
        EnergyClassBadge(item.energyClass)
        SectionCard("Паспорт") {
            InfoRow("Тип", building.type.title)
            InfoRow("Адреса", building.address)
            InfoRow("Рік побудови", building.yearBuilt.toString())
            InfoRow("Опалювана площа", "${building.heatedAreaM2.format()} м²")
            InfoRow("Статус", building.status.title)
            building.location?.let { InfoRow("Координати", "${it.latitude.format(4)}, ${it.longitude.format(4)}") }
        }
        SectionCard("Теплотехніка") {
            InfoRow("Коефіцієнт H", "${item.metrics.heatTransferWPerK.format()} Вт/К")
            InfoRow("Розрахункові втрати", "${item.metrics.designHeatLossKw.format(1)} кВт")
            InfoRow("Потреба в теплі", "${item.metrics.annualHeatGcal.format(1)} Гкал/рік")
            InfoRow("Вартість опалення", "${item.annualCostUah.format()} грн/рік")
        }
        SectionCard("Огородження") {
            building.envelope.forEach { element ->
                Text("${element.kind.title}: ${element.areaM2.format()} м², R = ${EnergyCalculator.resistance(element).format(2)} м²·К/Вт")
            }
        }
    }
}
