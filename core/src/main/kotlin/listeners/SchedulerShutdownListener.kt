package systems.choochoo.transit_data_archivers.core.listeners

import io.github.oshai.kotlinlogging.slf4j.toKLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.quartz.listeners.SchedulerListenerSupport
import java.util.concurrent.CountDownLatch

@Singleton
class SchedulerShutdownListener @Inject constructor() : SchedulerListenerSupport() {
    val schedulerShutdownLatch = CountDownLatch(1)

    private val log = super.log.toKLogger()

    @Volatile
    var schedulerStarted = false

    override fun schedulerStarted() {
        schedulerStarted = true
    }

    override fun schedulerShutdown() {
        log.info { "Scheduler has shut down; decrementing latch..." }
        schedulerShutdownLatch.countDown()
    }
}