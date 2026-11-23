package ua.kpi.building_audit.practical14

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.kpi.building_audit.data.repository.BuildingRepository
import ua.kpi.building_audit.domain.BuildingAssessment
import ua.kpi.building_audit.domain.BuildingQuery
import ua.kpi.building_audit.domain.EnergyCalculator
import ua.kpi.building_audit.domain.PortfolioAggregator
import ua.kpi.building_audit.domain.PortfolioSummary
import ua.kpi.building_audit.domain.model.GeoPoint
import ua.kpi.building_audit.domain.model.Tariffs
import ua.kpi.building_audit.location.LocationTracker
import ua.kpi.building_audit.ui.containerFactory

data class ExplorerUiState(
    val all: List<BuildingAssessment> = emptyList(),
    val visible: List<BuildingAssessment> = emptyList(),
    val query: BuildingQuery = BuildingQuery(),
    /** Об'єкт, на якому стоїть фокус мапи і списку. */
    val focusedId: Long? = null,
    /** Позначені прапорцями об'єкти для групового зведення (ПЗ 15). */
    val selectedIds: Set<Long> = emptySet(),
    val selectionSummary: PortfolioSummary = PortfolioSummary.EMPTY,
    val userLocation: GeoPoint? = null,
    val locationMessage: String? = null
) {
    val focused: BuildingAssessment? get() = all.firstOrNull { it.building.id == focusedId }
    /** Межі для слайдера потужності: від найменших до найбільших розрахункових втрат у реєстрі. */
    val lossBounds: ClosedFloatingPointRange<Double>
        get() = if (all.isEmpty()) 0.0..1.0 else all.minOf { it.metrics.designHeatLossKw }..all.maxOf { it.metrics.designHeatLossKw }
}

/** Спільна модель для екранів списку з мапою (ПЗ 14), фільтрів (ПЗ 15) і GPS (ПЗ 16). */
class BuildingsViewModel(
    private val repository: BuildingRepository,
    private val locationTracker: LocationTracker
) : ViewModel() {

    private val tariffs = MutableStateFlow(Tariffs.FALLBACK)
    private val query = MutableStateFlow(BuildingQuery())
    private val focusedId = MutableStateFlow<Long?>(null)
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val location = MutableStateFlow<GeoPoint?>(null)
    private val locationMessage = MutableStateFlow<String?>(null)
    private var locationJob: Job? = null

    private val assessed = combine(repository.observeCached(), tariffs) { buildings, tariff ->
        buildings.map { EnergyCalculator.assess(it, tariff) }
    }

    val state: StateFlow<ExplorerUiState> = combine(
        combine(assessed, query, location) { all, q, here -> Triple(all, q.copy(origin = here), here) },
        focusedId,
        selectedIds,
        locationMessage
    ) { (all, q, here), focus, selected, message ->
        ExplorerUiState(
            all = all,
            visible = q.apply(all),
            query = q,
            focusedId = focus,
            selectedIds = selected,
            selectionSummary = PortfolioAggregator.summarize(all.filter { it.building.id in selected }),
            userLocation = here,
            locationMessage = message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExplorerUiState())

    init {
        viewModelScope.launch {
            tariffs.value = repository.tariffs().value
            repository.refreshCache(force = false)
        }
    }

    fun focus(id: Long) {
        focusedId.value = id
    }

    fun updateQuery(transform: (BuildingQuery) -> BuildingQuery) = query.update(transform)

    fun toggleSelection(id: Long) = selectedIds.update { if (id in it) it - id else it + id }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun startLocationUpdates() {
        if (locationJob?.isActive == true) return
        locationJob = viewModelScope.launch {
            locationMessage.value = "Визначаю місцезнаходження..."
            locationTracker.updates()
                .catch { locationMessage.value = it.message }
                .collect {
                    location.value = it
                    locationMessage.value = null
                }
        }
    }

    companion object {
        val Factory = containerFactory { BuildingsViewModel(it.buildingRepository, it.locationTracker) }
    }
}
