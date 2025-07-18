@file:OptIn(ExperimentalTime::class)

package systems.choochoo.transit_data_archivers.common.utils

import kotlin.math.ceil
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class Backoff(
    private val maxConsecutiveFailures: Int,
    private val pausePeriod: Duration,
    private val pauseEscalation: Double,
    private val maxPauseDuration: Duration,
    private val pauseResetPeriod: Duration,
    private val jitterFactor: Double,
    private val quantum: Duration
) {
    private val failureTimes: MutableSet<Instant> = mutableSetOf()

    /**
     * Observe an execution event and return a backoff duration when warranted.
     *
     * The general logic is as follows:
     *
     * 1. The first `n` failures are "free", in the sense that they do not provoke a pause.
     * 2. Subsequent failures provoke a pause defined by an exponential escalation, up to a maximum duration.
     * 3. To avoid thundering herds, we perturb the pause period by randomly adding or removing a given jitter factor.
     *
     */
    fun observeExecution(executionTime: Instant, isError: Boolean): Duration? {
        if (isError) {
            failureTimes.add(executionTime)
        }

        failureTimes.removeIf {
            executionTime - it > pauseResetPeriod
        }

        val chargedFailures = failureTimes.size - maxConsecutiveFailures

        return if (isError && chargedFailures > 0) {
            quantizeDuration(
                jitter(
                    (pausePeriod * pauseEscalation.pow(chargedFailures)),
                    jitterFactor
                ),
                quantum
            )
                .coerceAtMost(maxPauseDuration)
        } else {
            null
        }
    }

    companion object {
        internal fun quantizeDuration(duration: Duration, quantum: Duration): Duration {
            return quantum * ceil(duration / quantum)
        }

        internal fun jitter(duration: Duration, jitterFactor: Double, random: Random = Random.Default): Duration {
            require(jitterFactor in 0.0..1.0)

            return if (jitterFactor > 0.0) {
                duration * random.nextDouble(1.0 - jitterFactor, 1.0 + jitterFactor)
            } else {
                duration
            }
        }
    }
}