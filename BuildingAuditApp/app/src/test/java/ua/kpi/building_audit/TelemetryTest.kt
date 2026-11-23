package ua.kpi.building_audit

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.kpi.building_audit.domain.telemetry.Reading
import ua.kpi.building_audit.domain.telemetry.SensorFailureException
import ua.kpi.building_audit.domain.telemetry.TelemetryOperators
import ua.kpi.building_audit.domain.telemetry.TelemetryOperators.retryOnSensorFailure
import ua.kpi.building_audit.domain.telemetry.TelemetryOperators.withinRange
import ua.kpi.building_audit.domain.telemetry.TelemetrySimulator
import kotlin.random.Random

class TelemetryTest {

    private fun simulator(failure: Double = 0.0, glitch: Double = 0.0, seed: Int = 14) =
        TelemetrySimulator(designHeatTransferWPerK = 6515.3, failureProbability = failure, glitchProbability = glitch, random = Random(seed), clock = { 0L })

    @Test
    fun coldFlowEmitsOncePerPeriod() = runTest {
        val readings = simulator().indoorTemperature().take(5).toList()
        assertEquals(5, readings.size)
        // Віртуальний час: чотири паузи по 1000 мс між п'ятьма значеннями.
        assertEquals(4_000, currentTime)
        assertTrue(readings.all { it.value in 19.0..21.0 })
    }

    @Test
    fun glitchesAreFilteredOut() = runTest {
        val raw = simulator(glitch = 0.5).indoorTemperature().take(40).toList()
        assertTrue(raw.any { it.value == TelemetrySimulator.GLITCH_VALUE })
        val clean = simulator(glitch = 0.5).indoorTemperature().withinRange(TelemetryOperators.PLAUSIBLE_INDOOR_RANGE).take(20).toList()
        assertTrue(clean.none { it.value == TelemetrySimulator.GLITCH_VALUE })
    }

    @Test
    fun sensorFailureIsRetriedWithDoublingDelay() = runTest {
        var attempts = 0
        val retries = mutableListOf<Long>()
        val source = flow {
            if (++attempts <= 2) throw SensorFailureException("timeout")
            emit(Reading(0, 21.0))
        }
        val values = source.retryOnSensorFailure(maxRetries = 5, initialDelayMs = 500) { attempt, _ -> retries += attempt }.toList()
        assertEquals(listOf(21.0), values.map { it.value })
        assertEquals(listOf(1L, 2L), retries)
        assertEquals(500L + 1_000L, currentTime)
    }

    @Test(expected = SensorFailureException::class)
    fun retryGivesUpAfterLimit() = runTest {
        flow<Reading> { throw SensorFailureException("dead") }.retryOnSensorFailure(maxRetries = 2, initialDelayMs = 10).toList()
    }

    @Test(expected = IllegalStateException::class)
    fun programmingErrorsAreNotRetried() = runTest {
        flow<Reading> { throw IllegalStateException("bug") }.retryOnSensorFailure(maxRetries = 5, initialDelayMs = 10).toList()
    }

    @Test
    fun combinedFlowRecoversHeatTransfer() = runTest {
        val design = 6515.3
        val diagnosis = TelemetryOperators.diagnose(
            indoor = flowOf(Reading(0, 20.0)),
            outdoor = flowOf(Reading(0, -5.0)),
            power = flowOf(Reading(0, design * 1.25 * 25 / 1000)),
            designHeatTransferWPerK = design
        ).toList().single()
        assertEquals(design * 1.25, diagnosis.measuredHeatTransferWPerK, 1e-6)
        assertEquals(25.0, diagnosis.deviationPercent, 1e-6)
    }

    @Test
    fun smallTemperatureDifferenceIsSkipped() = runTest {
        val result = TelemetryOperators.diagnose(flowOf(Reading(0, 20.0)), flowOf(Reading(0, 17.0)), flowOf(Reading(0, 10.0)), 1000.0).toList()
        assertTrue(result.isEmpty())
    }

    @Test
    fun simulatedDegradationIsDetected() = runTest {
        val sim = simulator()
        val samples = TelemetryOperators.diagnose(sim.indoorTemperature(), sim.outdoorTemperature(), sim.heatMeterPowerKw(), 6515.3).take(60).toList()
        val mean = samples.map { it.deviationPercent }.average()
        assertEquals(25.0, mean, 5.0)
    }
}
