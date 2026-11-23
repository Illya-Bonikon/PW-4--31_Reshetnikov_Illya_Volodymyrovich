package ua.kpi.building_audit.domain

import ua.kpi.building_audit.domain.geo.GeoMath
import ua.kpi.building_audit.domain.model.AuditStatus
import ua.kpi.building_audit.domain.model.BuildingType
import ua.kpi.building_audit.domain.model.GeoPoint

enum class SortOrder(val title: String) {
    SPECIFIC_LOSS("Тепловтрати"),
    ANNUAL_COST("Вартість"),
    AREA("Площа"),
    NAME("Назва"),
    DISTANCE("Відстань")
}

/**
 * Пошук, багаторівневий фільтр і сортування реєстру. Порожня множина у фільтрі означає «усі».
 * При однакових значеннях ключа сортування порядок визначає назва, тому список стабільний.
 */
data class BuildingQuery(
    val text: String = "",
    val classes: Set<EnergyClass> = emptySet(),
    val sort: SortOrder = SortOrder.SPECIFIC_LOSS,
    val types: Set<BuildingType> = emptySet(),
    val statuses: Set<AuditStatus> = emptySet(),
    /** Діапазон розрахункових тепловтрат, кВт; null — без обмеження. */
    val designLossKw: ClosedFloatingPointRange<Double>? = null,
    val descending: Boolean = true,
    val origin: GeoPoint? = null,
    val radiusKm: Double? = null
) {
    fun distanceKm(item: BuildingAssessment): Double? {
        val from = origin ?: return null
        val to = item.building.location ?: return null
        return GeoMath.distanceKm(from, to)
    }

    fun apply(items: List<BuildingAssessment>): List<BuildingAssessment> {
        val needle = text.trim().lowercase()
        val filtered = items.filter { item ->
            val building = item.building
            (classes.isEmpty() || item.energyClass in classes) &&
                (types.isEmpty() || building.type in types) &&
                (statuses.isEmpty() || building.status in statuses) &&
                (designLossKw == null || item.metrics.designHeatLossKw in designLossKw) &&
                (radiusKm == null || (distanceKm(item)?.let { it <= radiusKm } ?: false)) &&
                (needle.isEmpty() || needle in building.name.lowercase() || needle in building.address.lowercase())
        }
        val byName = compareBy<BuildingAssessment> { it.building.name.lowercase() }
        val primary: Comparator<BuildingAssessment> = when (sort) {
            SortOrder.SPECIFIC_LOSS -> compareBy { it.metrics.specificHeatLossWPerM2 }
            SortOrder.ANNUAL_COST -> compareBy { it.annualCostUah }
            SortOrder.AREA -> compareBy { it.building.heatedAreaM2 }
            SortOrder.NAME -> byName
            // Об'єкти без координат опиняються в кінці списку.
            SortOrder.DISTANCE -> compareBy { distanceKm(it) ?: Double.MAX_VALUE }
        }
        val ordered = if (descending && sort != SortOrder.NAME && sort != SortOrder.DISTANCE) primary.reversed() else primary
        return filtered.sortedWith(ordered.then(byName))
    }

    fun toggle(energyClass: EnergyClass): BuildingQuery = copy(classes = classes.toggle(energyClass))
    fun toggle(type: BuildingType): BuildingQuery = copy(types = types.toggle(type))
    fun toggle(status: AuditStatus): BuildingQuery = copy(statuses = statuses.toggle(status))

    private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
}
