@file:OptIn(ExperimentalTime::class)

package systems.choochoo.transit_data_archivers.gtfsrt.listeners

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.github.oshai.kotlinlogging.slf4j.toKLogger
import org.quartz.*
import org.quartz.listeners.JobListenerSupport
import systems.choochoo.transit_data_archivers.common.utils.Backoff
import systems.choochoo.transit_data_archivers.gtfsrt.Configuration
import systems.choochoo.transit_data_archivers.gtfsrt.entities.FeedContents
import systems.choochoo.transit_data_archivers.gtfsrt.jobs.JobUnpauserJob
import java.util.*
import kotlin.time.*

@AssistedFactory
internal interface JobFailureListenerFactory {
    fun create(key: JobKey): JobFailureListener
}

internal class JobFailureListener @AssistedInject constructor(
    configuration: Configuration,
    @Assisted private val key: JobKey
) : JobListenerSupport() {
    private val frc = configuration.failureResponse

    private val log = super.log.toKLogger()

    private val b = Backoff(
        frc.maxConsecutiveFailures,
        frc.pausePeriod,
        frc.pauseEscalation,
        frc.maxPauseDuration,
        frc.pauseResetPeriod,
        frc.jitterFactor,
        frc.quantum
    )

    override fun getName(): String = "JobFailureListener for $key"

    override fun jobWasExecuted(context: JobExecutionContext, jobException: JobExecutionException?) {
        val fireTime = context.fireTime.toInstant().toKotlinInstant()

        val threwException = jobException != null
        val reportedError = (context.result as FeedContents?)?.isError == true

        val pauseDuration = b.observeExecution(fireTime, threwException || reportedError)

        if (pauseDuration != null) {
            val scheduler = context.scheduler
            val jobKey = context.jobDetail.key

            log.warn { "Pausing execution of job $jobKey for $pauseDuration due to consecutive failure count exceeding ${frc.maxConsecutiveFailures}" }

            scheduler.pauseJob(jobKey)

            val jobDataMap = JobDataMap()
            jobDataMap["jobKey"] = jobKey

            val unpauseJob = JobBuilder.newJob(JobUnpauserJob::class.java).setJobData(jobDataMap).build()

            val unpauseTrigger =
                TriggerBuilder.newTrigger().startAt(Date.from((Clock.System.now() + pauseDuration).toJavaInstant()))
                    .build()

            scheduler.scheduleJob(unpauseJob, unpauseTrigger)
        }
    }
}
