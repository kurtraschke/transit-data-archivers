package systems.choochoo.transit_data_archivers.gtfsrt.modules

import dagger.Binds
import dagger.Module
import org.quartz.spi.JobFactory
import systems.choochoo.transit_data_archivers.gtfsrt.DaggerJobFactory

@Module
internal abstract class DaggerJobFactoryModule {
    @Binds
    abstract fun jobFactory(daggerJobFactory: DaggerJobFactory): JobFactory
}