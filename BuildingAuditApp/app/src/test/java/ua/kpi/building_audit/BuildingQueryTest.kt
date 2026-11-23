package ua.kpi.building_audit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.kpi.building_audit.domain.BuildingQuery
import ua.kpi.building_audit.domain.EnergyCalculator
import ua.kpi.building_audit.domain.SortOrder
import ua.kpi.building_audit.domain.geo.GeoMath
import ua.kpi.building_audit.domain.model.AuditStatus
import ua.kpi.building_audit.domain.model.BuildingType
import ua.kpi.building_audit.domain.model.GeoPoint

class BuildingQueryTest {
    private val kpi = GeoPoint(50.4489, 30.4572)
    private val items = listOf(
        DORMITORY.copy(id = 1, name = "Гуртожиток", location = GeoPoint(50.4468, 30.4470), status = AuditStatus.NEEDS_RETROFIT),
        DORMITORY.copy(id = 2, name = "Офіс", type = BuildingType.OFFICE, location = GeoPoint(50.4557, 30.4426), status = AuditStatus.CERTIFIED,
            envelope = DORMITORY.envelope.map { it.copy(areaM2 = it.areaM2 / 2) }),
        DORMITORY.copy(id = 3, name = "Без координат", location = null, status = AuditStatus.IN_PROGRESS)
    ).map { EnergyCalculator.assess(it, TARIFFS) }

    @Test
    fun haversineMatchesKnownDistance() {
        // Київ — Львів по прямій близько 468 км.
        assertEquals(468.0, GeoMath.distanceKm(GeoPoint(50.4501, 30.5234), GeoPoint(49.8397, 24.0297)), 3.0)
        assertEquals(0.0, GeoMath.distanceKm(kpi, kpi), 1e-9)
    }

    @Test
    fun filtersCombineAcrossLevels() {
        assertEquals(listOf(2L), BuildingQuery(types = setOf(BuildingType.OFFICE)).apply(items).map { it.building.id })
        assertEquals(listOf(3L), BuildingQuery(statuses = setOf(AuditStatus.IN_PROGRESS)).apply(items).map { it.building.id })
        assertEquals(emptyList<Long>(), BuildingQuery(types = setOf(BuildingType.OFFICE), statuses = setOf(AuditStatus.IN_PROGRESS)).apply(items))
    }

    @Test
    fun designLossRangeKeepsOnlySmallerBuilding() {
        val small = items.first { it.building.id == 2L }.metrics.designHeatLossKw
        assertEquals(listOf(2L), BuildingQuery(designLossKw = 0.0..small + 1).apply(items).map { it.building.id })
    }

    @Test
    fun distanceSortAndRadius() {
        val query = BuildingQuery(sort = SortOrder.DISTANCE, origin = kpi)
        // Без координат — в кінці; решта за зростанням відстані.
        assertEquals(listOf(1L, 2L, 3L), query.apply(items).map { it.building.id })
        assertEquals(listOf(1L), query.copy(radiusKm = 0.9).apply(items).map { it.building.id })
        assertNull(BuildingQuery().distanceKm(items[0]))
    }

    @Test
    fun descendingToggleReversesOrder() {
        val desc = BuildingQuery(sort = SortOrder.AREA, descending = true).apply(items).map { it.building.id }
        val asc = BuildingQuery(sort = SortOrder.AREA, descending = false).apply(items).map { it.building.id }
        // Однакова площа: порядок визначає назва, тому обидва списки відсортовані за назвою.
        assertEquals(asc, desc)
        val byLoss = BuildingQuery(sort = SortOrder.SPECIFIC_LOSS).apply(items)
        assertEquals(byLoss.reversed().map { it.metrics.specificHeatLossWPerM2 }, BuildingQuery(sort = SortOrder.SPECIFIC_LOSS, descending = false).apply(items).map { it.metrics.specificHeatLossWPerM2 })
    }
}
