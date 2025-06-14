package systems.choochoo.transit_data_archivers.core.modules

import dagger.Module
import dagger.Provides
import jakarta.inject.Singleton
import org.quartz.Scheduler
import org.quartz.impl.StdSchedulerFactory
import org.quartz.spi.JobFactory
import systems.choochoo.transit_data_archivers.core.listeners.SchedulerErrorListener
import systems.choochoo.transit_data_archivers.core.listeners.SchedulerShutdownListener

@Module
class QuartzSchedulerModule {
    companion object {
        @Provides
        @Singleton
        fun provideScheduler(
            jobFactory: JobFactory,
            schedulerErrorListener: SchedulerErrorListener,
            schedulerShutdownListener: SchedulerShutdownListener
        ): Scheduler {
            val scheduler = StdSchedulerFactory.getDefaultScheduler()
            scheduler.listenerManager.addSchedulerListener(schedulerErrorListener)
            scheduler.listenerManager.addSchedulerListener(schedulerShutdownListener)

            scheduler.setJobFactory(jobFactory)

            return scheduler
        }
    }
}