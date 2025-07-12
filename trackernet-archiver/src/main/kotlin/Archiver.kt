@file:OptIn(ExperimentalTime::class)

package systems.choochoo.transit_data_archivers.trackernet

import dagger.BindsInstance
import dagger.Component
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.quartz.JobBuilder
import org.quartz.JobDataMap
import org.quartz.Scheduler
import org.quartz.TriggerBuilder
import org.quartz.impl.matchers.KeyMatcher
import systems.choochoo.transit_data_archivers.core.listeners.OneShotJobCompletionListener
import systems.choochoo.transit_data_archivers.core.listeners.SchedulerErrorListener
import systems.choochoo.transit_data_archivers.core.listeners.SchedulerShutdownListener
import systems.choochoo.transit_data_archivers.core.modules.ApplicationVersionModule
import systems.choochoo.transit_data_archivers.core.modules.ClickHouseClientModule
import systems.choochoo.transit_data_archivers.core.modules.CookieHandlerModule
import systems.choochoo.transit_data_archivers.core.modules.OkHttpClientModule
import systems.choochoo.transit_data_archivers.core.modules.QuartzSchedulerModule
import systems.choochoo.transit_data_archivers.core.utils.randomDuration
import systems.choochoo.transit_data_archivers.trackernet.jobs.LineArchiveJob
import systems.choochoo.transit_data_archivers.trackernet.listeners.FixedDelayJobListener
import systems.choochoo.transit_data_archivers.trackernet.modules.ConfigurationModule
import systems.choochoo.transit_data_archivers.trackernet.modules.DaggerJobFactoryModule
import systems.choochoo.transit_data_archivers.trackernet.modules.TrackernetApiClientModule
import java.util.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

private val log = KotlinLogging.logger {}

private const val FETCH_GROUP = "line-fetch"

@Component(
    modules = [
        ConfigurationModule::class,
        ApplicationVersionModule::class,
        ClickHouseClientModule::class,
        DaggerJobFactoryModule::class,
        QuartzSchedulerModule::class,
        CookieHandlerModule::class,
        OkHttpClientModule::class,
        TrackernetApiClientModule::class
    ]
)
@Singleton
internal interface ArchiverFactory {
    fun archiver(): Archiver
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
    configuration: Configuration,
    @param:Named("oneShot") val oneShot: Boolean,
    val scheduler: Scheduler
) {
    init {
        val jobs = configuration.lines.map { lc ->
            val jobDataMap = JobDataMap()

            jobDataMap["lineCode"] = lc.lineCode
            jobDataMap["excludedStations"] = lc.excludedStations

            val job = JobBuilder.newJob(LineArchiveJob::class.java)
                .withIdentity(lc.lineCode, FETCH_GROUP)
                .usingJobData(jobDataMap)
                .build()

            val trigger = TriggerBuilder.newTrigger()
                .withIdentity(lc.lineCode, FETCH_GROUP)
                .let {
                    if (oneShot) {
                        it.startNow()
                    } else {
                        val startTime = Clock.System.now() + randomDuration(lc.fetchInterval.v)
                        it.startAt(Date.from(startTime.toJavaInstant()))
                    }
                }
                .build()

            if (!oneShot) {
                scheduler.listenerManager.addJobListener(
                    FixedDelayJobListener(job.key, lc.fetchInterval.v),
                    KeyMatcher.keyEquals(job.key)
                )
            }

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