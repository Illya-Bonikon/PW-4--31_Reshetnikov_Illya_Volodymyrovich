package ua.kpi.building_audit.practical13

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import ua.kpi.building_audit.data.repository.BuildingRepository
import ua.kpi.building_audit.domain.EnergyCalculator
import ua.kpi.building_audit.domain.model.Building
import ua.kpi.building_audit.domain.model.Tariffs
import ua.kpi.building_audit.domain.telemetry.EnvelopeDiagnosis
import ua.kpi.building_audit.domain.telemetry.Reading
import ua.kpi.building_audit.domain.telemetry.TelemetryOperators
import ua.kpi.building_audit.domain.telemetry.TelemetryOperators.retryOnSensorFailure
import ua.kpi.building_audit.domain.telemetry.TelemetryOperators.withinRange
import ua.kpi.building_audit.domain.telemetry.TelemetrySimulator
import ua.kpi.building_audit.ui.containerFactory
import ua.kpi.building_audit.ui.formatTime

data class TelemetryUiState(
    val buildings: List<Building> = emptyList(),
    val selected: Building? = null,
    val indoor: List<Double> = emptyList(),
    val outdoor: List<Double> = emptyList(),
    val power: List<Double> = emptyList(),
    val diagnosis: EnvelopeDiagnosis? = null,
    val setpoint: String = "21",
    val setpointPowerKw: Double? = null,
    val rejectedGlitches: Int = 0,
    val events: List<String> = emptyList()
)

private data class Session(val building: Building, val designHeatTransfer: Double, val simulator: TelemetrySimulator, val generation: Int)

/**
 * Три джерела стають «гарячими» через shareIn: їх одночасно збирають екран і комбінований потік
 * діагностики, але генерація для кожного джерела одна. Зміна будівлі або перезапуск
 * через flatMapLatest скасовує старі потоки й починає нові.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TelemetryViewModel(repository: BuildingRepository) : ViewModel() {

    private val buildings = repository.observeCached().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val selectedId = MutableStateFlow<Long?>(null)
    private val restarts = MutableStateFlow(0)
    private val setpoint = MutableStateFlow("21")
    private val glitches = MutableStateFlow(0)
    private val events = MutableStateFlow<List<String>>(emptyList())

    private val session: Flow<Session?> = combine(buildings, selectedId, restarts) { list, id, generation ->
        val building = list.firstOrNull { it.id == id } ?: list.firstOrNull() ?: return@combine null
        val design = EnergyCalculator.metrics(building.envelope, Tariffs.FALLBACK.climate).heatTransferWPerK
        Session(building, design, TelemetrySimulator(design), generation)
    }.distinctUntilChanged { old, new -> old?.building?.id == new?.building?.id && old?.generation == new?.generation }

    private val indoor = hot { simulator ->
        simulator.indoorTemperature()
            .onEach { if (it.value !in TelemetryOperators.PLAUSIBLE_INDOOR_RANGE) glitches.update { count -> count + 1 } }
            .withinRange(TelemetryOperators.PLAUSIBLE_INDOOR_RANGE)
            .retryOnSensorFailure(maxRetries = 5, initialDelayMs = 500) { attempt, cause -> log("${cause.message}: повтор $attempt з 5") }
            .catch { log("Датчик температури відключено після 5 невдалих спроб") }
    }
    private val outdoor = hot { it.outdoorTemperature() }
    private val power = hot { it.heatMeterPowerKw() }

    private val diagnosis: Flow<EnvelopeDiagnosis?> = session.flatMapLatest { current ->
        if (current == null) emptyFlow()
        else TelemetryOperators.diagnose(indoor, outdoor, power, current.designHeatTransfer)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Debounce: прогноз перераховується, коли користувач перестав друкувати на 500 мс. */
    private val setpointPower: Flow<Double?> = combine(setpoint.debounce(500).map { it.replace(',', '.').toDoubleOrNull() }, diagnosis) { target, d ->
        if (target == null || d == null || target !in 10.0..30.0) null
        else d.measuredHeatTransferWPerK * (target - d.outdoorC) / 1000.0
    }

    val state: StateFlow<TelemetryUiState> = combine(
        combine(buildings, session, setpoint) { list, current, text -> Triple(list, current?.building, text) },
        session.flatMapLatest { combine(indoor.history(), outdoor.history(), power.history()) { i, o, p -> Triple(i, o, p) } },
        combine(diagnosis, setpointPower) { d, p -> d to p },
        glitches,
        events
    ) { (list, building, text), (i, o, p), (d, forecast), rejected, log ->
        TelemetryUiState(list, building, i, o, p, d, text, forecast, rejected, log)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TelemetryUiState())

    fun select(building: Building) {
        selectedId.value = building.id
        glitches.value = 0
        log("Обрано: ${building.name}")
    }

    fun restart() {
        restarts.update { it + 1 }
        log("Потоки перезапущено")
    }

    fun onSetpointChange(value: String) {
        setpoint.value = value
    }

    private fun hot(source: (TelemetrySimulator) -> Flow<Reading>): Flow<Reading> =
        session.flatMapLatest { current -> current?.let { source(it.simulator) } ?: emptyFlow() }
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private fun Flow<Reading>.history(size: Int = 40): Flow<List<Double>> =
        runningFold(emptyList<Double>()) { acc, reading -> (acc + reading.value).takeLast(size) }

    private fun log(message: String) {
        events.update { (listOf("${formatTime(System.currentTimeMillis())}  $message") + it).take(8) }
    }

    companion object {
        val Factory = containerFactory { TelemetryViewModel(it.buildingRepository) }
    }
}
