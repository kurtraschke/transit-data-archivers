package systems.choochoo.transit_data_archivers.common.configuration

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class FailureResponse(
    val maxConsecutiveFailures: Int = 5,
    val pausePeriod: Duration = 30.seconds,
    val pauseEscalation: Double =  1.2,
    val maxPauseDuration: Duration = 15.minutes,
    val pauseResetPeriod: Duration = 1.hours,
    val jitterFactor: Double = 0.1,
    val quantum: Duration = 15.seconds
)