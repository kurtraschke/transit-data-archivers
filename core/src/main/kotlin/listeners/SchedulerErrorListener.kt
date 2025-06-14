package systems.choochoo.transit_data_archivers.core.listeners

import dagger.Lazy
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.quartz.Scheduler
import org.quartz.SchedulerException
import org.quartz.listeners.SchedulerListenerSupport
import kotlin.concurrent.thread

@Singleton
class SchedulerErrorListener @Inject constructor() : SchedulerListenerSupport() {
    @Volatile
    var schedulerTerminatedWithError = false

    @Inject
    lateinit var lazyScheduler: Lazy<Scheduler>

    override fun schedulerError(msg: String, cause: SchedulerException) {
        log.error("Shutting down due to critical error", cause)
        schedulerTerminatedWithError = true
        thread {
            lazyScheduler.get().shutdown()
        }
    }
}