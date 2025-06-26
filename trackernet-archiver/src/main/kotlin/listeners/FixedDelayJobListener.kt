@file:OptIn(ExperimentalTime::class)

package systems.choochoo.transit_data_archivers.trackernet.listeners

import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.quartz.JobKey
import org.quartz.TriggerBuilder.newTrigger
import org.quartz.listeners.JobListenerSupport
import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

internal class FixedDelayJobListener(val key: JobKey, val interval: Duration) : JobListenerSupport() {
    override fun getName(): String = "FixedDelayJobListener for $key"

    override fun jobWasExecuted(context: JobExecutionContext, jobException: JobExecutionException?) {
        val scheduler = context.scheduler

        val jobKey = context.jobDetail.key

        val triggerKey = context.trigger.key

        val nextFireTime = Clock.System.now() + interval

        val newTrigger = newTrigger()
            .withIdentity(triggerKey)
            .startAt(Date.from(nextFireTime.toJavaInstant()))
            .build()

        log.debug("Rescheduling job {} for {}", jobKey, nextFireTime)

        scheduler.rescheduleJob(triggerKey, newTrigger)
    }

}