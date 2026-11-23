package ua.kpi.building_audit.domain.telemetry

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

data class Reading(val timestampMs: Long, val value: Double)

/** Збій зв'язку з датчиком; вважається тимчасовим, тому потік перезапускається. */
class SensorFailureException(message: String) : IOException(message)

/**
 * Імітація трьох джерел даних будівлі: датчик температури в приміщенні, метеомодель
 * зовнішньої температури і теплолічильник. Кожен метод повертає холодний Flow:
 * генерація починається заново для кожного збирача.
 *
 * Теплолічильник рахує потужність з фактичного коефіцієнта теплопередачі, який на
 * [degradation] більший за проєктний: так комбінований потік має що виявити.
 */
class TelemetrySimulator(
    private val designHeatTransferWPerK: Double,
    private val periodMs: Long = 1_000,
    private val degradation: Double = 0.25,
    private val failureProbability: Double = 0.04,
    private val glitchProbability: Double = 0.05,
    private val random: Random = Random.Default,
    private val clock: () -> Long = System::currentTimeMillis
) {
    fun indoorTemperature(): Flow<Reading> = sensor { tick ->
        if (random.nextDouble() < failureProbability) throw SensorFailureException("Датчик температури не відповідає")
        // Збій перетворювача дає значення поза фізичним діапазоном; його відкидає filter.
        if (random.nextDouble() < glitchProbability) GLITCH_VALUE else 20.0 + 0.6 * sin(tick / 8.0) + noise(0.15)
    }

    fun outdoorTemperature(): Flow<Reading> = sensor { tick -> outdoorAt(tick) + noise(0.3) }

    fun heatMeterPowerKw(): Flow<Reading> = sensor { tick ->
        val deltaT = 20.0 - outdoorAt(tick)
        designHeatTransferWPerK * (1 + degradation) * deltaT / 1000.0 + noise(2.0)
    }

    private fun outdoorAt(tick: Long): Double = -5.0 + 4.0 * sin(2 * PI * tick / 120.0)

    private fun noise(amplitude: Double): Double = (random.nextDouble() * 2 - 1) * amplitude

    private fun sensor(sample: (Long) -> Double): Flow<Reading> = flow {
        var tick = 0L
        while (true) {
            emit(Reading(clock(), sample(tick++)))
            delay(periodMs)
        }
    }

    companion object {
        const val GLITCH_VALUE = 999.0
    }
}

/** Фактичний стан оболонки, обчислений з трьох потоків одночасно. */
data class EnvelopeDiagnosis(
    val indoorC: Double,
    val outdoorC: Double,
    val powerKw: Double,
    val measuredHeatTransferWPerK: Double,
    val designHeatTransferWPerK: Double
) {
    /** Наскільки фактичні тепловтрати більші за проєктні, %. */
    val deviationPercent: Double get() = (measuredHeatTransferWPerK / designHeatTransferWPerK - 1) * 100
}

object TelemetryOperators {
    val PLAUSIBLE_INDOOR_RANGE = -30.0..60.0

    fun Flow<Reading>.withinRange(range: ClosedFloatingPointRange<Double>): Flow<Reading> = filter { it.value in range }

    /**
     * Перезапускає потік після SensorFailureException з паузою, що подвоюється.
     * Інші винятки не перехоплюються: це помилки програми, а не датчика.
     */
    fun <T> Flow<T>.retryOnSensorFailure(
        maxRetries: Long,
        initialDelayMs: Long,
        onRetry: (attempt: Long, cause: Throwable) -> Unit = { _, _ -> }
    ): Flow<T> = retryWhen { cause, attempt ->
        val retry = cause is SensorFailureException && attempt < maxRetries
        if (retry) {
            onRetry(attempt + 1, cause)
            delay(initialDelayMs shl attempt.toInt().coerceAtMost(10))
        }
        retry
    }

    /**
     * H = P / (Tв − Tз). Коли перепад температур менший за 5 °C, оцінка ненадійна
     * (ділення на мале число), і такі моменти пропускаються.
     */
    fun diagnose(indoor: Flow<Reading>, outdoor: Flow<Reading>, power: Flow<Reading>, designHeatTransferWPerK: Double): Flow<EnvelopeDiagnosis> =
        combine(indoor, outdoor, power) { tin, tout, p ->
            val deltaT = tin.value - tout.value
            if (deltaT < 5.0) null
            else EnvelopeDiagnosis(tin.value, tout.value, p.value, p.value * 1000 / deltaT, designHeatTransferWPerK)
        }.filterNotNull()
}
