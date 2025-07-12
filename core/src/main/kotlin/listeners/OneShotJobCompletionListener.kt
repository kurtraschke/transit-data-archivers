package systems.choochoo.transit_data_archivers.core.listeners

import io.github.oshai.kotlinlogging.slf4j.toKLogger
import org.quartz.JobDetail
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.quartz.listeners.JobListenerSupport
import kotlin.concurrent.thread

class OneShotJobCompletionListener(jobs: List<JobDetail>) : JobListenerSupport() {
    private val jobKeys = jobs.map { it.key }.toMutableSet()
    private val log = super.log.toKLogger()

    override fun getName(): String = "OneShotJobCompletionListener"

    override fun jobWasExecuted(context: JobExecutionContext, jobException: JobExecutionException?) {
        val jobKey = context.jobDetail.key

        synchronized(jobKeys) {
            jobKeys.remove(jobKey)

            log.trace { "remaining jobs: $jobKeys" }

            if (jobKeys.isEmpty()) {
                log.info { "No more work to do; shutting down scheduler..." }
                thread {
                    context.scheduler.shutdown(true)
                }
            }
        }
    }
}