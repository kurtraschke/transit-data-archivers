@file:OptIn(ExperimentalTime::class)

package systems.choochoo.transit_data_archivers.gtfsrt

import dagger.BindsInstance
import dagger.Component
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.quartz.JobBuilder.newJob
import org.quartz.JobDataMap
import org.quartz.Scheduler
import org.quartz.SimpleScheduleBuilder.simpleSchedule
import org.quartz.TriggerBuilder.newTrigger
import org.quartz.impl.matchers.KeyMatcher
import systems.choochoo.transit_data_archivers.core.configuration.ApplicationVersion
import systems.choochoo.transit_data_archivers.core.listeners.OneShotJobCompletionListener
import systems.choochoo.transit_data_archivers.core.listeners.SchedulerErrorListener
import systems.choochoo.transit_data_archivers.core.listeners.SchedulerShutdownListener
import systems.choochoo.transit_data_archivers.core.modules.ApplicationVersionModule
import systems.choochoo.transit_data_archivers.core.modules.ClickHouseClientModule
import systems.choochoo.transit_data_archivers.core.modules.CookieHandlerModule
import systems.choochoo.transit_data_archivers.core.modules.OkHttpClientModule
import systems.choochoo.transit_data_archivers.core.modules.QuartzSchedulerModule
import systems.choochoo.transit_data_archivers.core.utils.randomDuration
import systems.choochoo.transit_data_archivers.gtfsrt.jobs.FeedArchiveJob
import systems.choochoo.transit_data_archivers.gtfsrt.listeners.JobFailureListener
import systems.choochoo.transit_data_archivers.gtfsrt.modules.DaggerJobFactoryModule
import systems.choochoo.transit_data_archivers.gtfsrt.modules.ConfigurationModule
import java.util.Date
import kotlin.time.Clock
import kotlin.time.DurationUnit.*
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

private val log = KotlinLogging.logger {}

@Component(
    modules = [
        ConfigurationModule::class,
        ApplicationVersionModule::class,
        ClickHouseClientModule::class,
        CookieHandlerModule::class,
        OkHttpClientModule::class,
        DaggerJobFactoryModule::class,
        QuartzSchedulerModule::class,
    ]
)
@Singleton
internal interface ArchiverFactory {
    fun archiver(): Archiver
    fun appVersion(): ApplicationVersion
    fun schedulerErrorListener(): SchedulerErrorListener
    fun schedulerShutdownListener(): SchedulerShutdownListener

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun configuration(configuration: Configuration): Builder

        @BindsInstance
        fun oneShot(@Named("oneShot") oneShot: Boolean): Builder

        fun build(): ArchiverFactory
    }
}

internal class Archiver @Inject constructor(
    val configuration: Configuration,
    @param:Named("oneShot") val oneShot: Boolean,
    val scheduler: Scheduler,
) {
    init {
        val jobs = configuration.feeds.map { feed ->
            val fetchInterval = (feed.fetchInterval.getOrNull() ?: configuration.fetchInterval).v
            val storeResponseBody = feed.storeResponseBody ?: configuration.storeResponseBody
            val storeResponseBodyOnError = feed.storeResponseBodyOnError ?: configuration.storeResponseBodyOnError

            val jobDataMap = JobDataMap()
            jobDataMap["feed"] = feed
            jobDataMap["storeResponseBody"] = storeResponseBody
            jobDataMap["storeResponseBodyOnError"] = storeResponseBodyOnError

            val job = newJob(FeedArchiveJob::class.java)
                .withIdentity(feed.feed, feed.producer)
                .usingJobData(jobDataMap)
                .build()

            scheduler.listenerManager.addJobListener(JobFailureListener(job.key), KeyMatcher.keyEquals(job.key))

            val trigger = newTrigger()
                .withIdentity(feed.feed, feed.producer)
                .let {
                    if (oneShot) {
                        it.startNow()
                    } else {
                        val startTime = Clock.System.now() + randomDuration(fetchInterval)
                        it
                            .startAt(Date.from(startTime.toJavaInstant()))
                            .withSchedule(
                                simpleSchedule()
                                    .withIntervalInSeconds(fetchInterval.toInt(SECONDS))
                                    .repeatForever()
                            )
                    }
                }
                .build()

            scheduler.scheduleJob(job, trigger)

            job
        }

        if (oneShot) {
            scheduler.listenerManager.addJobListener(OneShotJobCompletionListener(jobs))
        }

    }

    fun start() {
        scheduler.start()
        log.info { "Archiver has started." }
    }

    fun stop() {
        scheduler.shutdown(true)
        log.warn { "Archiver has stopped." }
    }
}