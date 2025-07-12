package systems.choochoo.transit_data_archivers.core.listeners

import dagger.Lazy
import io.github.oshai.kotlinlogging.slf4j.toKLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.quartz.Scheduler
import org.quartz.SchedulerException
import org.quartz.listeners.SchedulerListenerSupport
import kotlin.concurrent.thread

@Singleton
class SchedulerErrorListener @Inject constructor(private val lazyScheduler: Lazy<Scheduler>) :
    SchedulerListenerSupport() {
    @Volatile
    var schedulerTerminatedWithError = false

    private val log = super.log.toKLogger()

    override fun schedulerError(msg: String, cause: SchedulerException) {
        log.error(cause) { "Shutting down due to critical error" }
        schedulerTerminatedWithError = true
        thread {
            lazyScheduler.get().shutdown()
        }
    }
}