@file:OptIn(ExperimentalTime::class)

package systems.choochoo.transit_data_archivers.gtfsrt.listeners

import io.github.oshai.kotlinlogging.slf4j.toKLogger
import org.quartz.*
import org.quartz.DateBuilder.IntervalUnit.SECOND
import org.quartz.DateBuilder.futureDate
import org.quartz.listeners.JobListenerSupport
import systems.choochoo.transit_data_archivers.gtfsrt.entities.FeedContents
import systems.choochoo.transit_data_archivers.gtfsrt.jobs.JobUnpauserJob
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

private const val MAX_CONSECUTIVE_FAILURES = 3
private val PAUSE_PERIOD = 30.seconds
private const val PAUSE_ESCALATION = 2.0
private val MAX_PAUSE_DURATION = 1.hours
private val RESET_PAUSE_AFTER = 6.hours

internal class JobFailureListener(private val key: JobKey) : JobListenerSupport() {
    private var consecutiveFailureCount = 0
    private var pauseCount = 0
    private var lastFailure: Instant? = null

    private val log = super.log.toKLogger()

    override fun getName(): String = "JobFailureListener for ${this.key}"

    override fun jobWasExecuted(context: JobExecutionContext, jobException: JobExecutionException?) {
        val threwException = jobException != null
        val reportedError = (context.result as FeedContents?)?.isError == true

        val scheduler = context.scheduler
        val jobKey = context.jobDetail.key

        val fireTime = context.fireTime.toInstant().toKotlinInstant()

        if (threwException || reportedError) {
            consecutiveFailureCount++
            lastFailure = fireTime

            if (consecutiveFailureCount >= MAX_CONSECUTIVE_FAILURES) {
                consecutiveFailureCount = 0
                pauseCount++

                val pauseDuration = quantizeDuration(
                    jitter((PAUSE_PERIOD * PAUSE_ESCALATION.pow(pauseCount)), 0.1),
                    15.seconds
                )
                    .coerceAtMost(MAX_PAUSE_DURATION)

                log.warn { "Pausing execution of job $jobKey for $pauseDuration due to consecutive failure count exceeding $MAX_CONSECUTIVE_FAILURES" }

                scheduler.pauseJob(jobKey)

                val jobDataMap = JobDataMap()
                jobDataMap["jobKey"] = jobKey

                val unpauseJob = JobBuilder.newJob(JobUnpauserJob::class.java).setJobData(jobDataMap).build()

                val unpauseTrigger =
                    TriggerBuilder.newTrigger().startAt(futureDate(pauseDuration.inWholeSeconds.toInt(), SECOND))
                        .build()

                scheduler.scheduleJob(unpauseJob, unpauseTrigger)
            }
        } else {
            consecutiveFailureCount = 0

            lastFailure?.let {
                if ((it - fireTime).absoluteValue >= RESET_PAUSE_AFTER) {
                    log.info { "Resetting pause count for job $jobKey as the last failure was at least $RESET_PAUSE_AFTER ago" }
                    pauseCount = 0
                    lastFailure = null
                }
            }
        }
    }
}

internal fun quantizeDuration(duration: Duration, interval: Duration): Duration {
    return interval * ceil(duration / interval)
}

internal fun jitter(duration: Duration, jitterFactor: Double, random: Random = Random.Default): Duration {
    require(jitterFactor in 0.0..1.0)

    return duration * random.nextDouble(1.0 - jitterFactor, 1.0)
}
