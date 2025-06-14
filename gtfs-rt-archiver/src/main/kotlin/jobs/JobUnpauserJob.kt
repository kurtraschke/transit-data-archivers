package systems.choochoo.transit_data_archivers.gtfsrt.jobs

import io.github.oshai.kotlinlogging.KotlinLogging
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobKey

private val log = KotlinLogging.logger {}

internal class JobUnpauserJob : Job {
    lateinit var jobKey: JobKey

    override fun execute(context: JobExecutionContext) {
        log.info { "Unpausing execution of job $jobKey" }
        context.scheduler.resumeJob(jobKey)
    }
}
