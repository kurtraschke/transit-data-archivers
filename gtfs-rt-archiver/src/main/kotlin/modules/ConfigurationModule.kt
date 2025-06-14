package systems.choochoo.transit_data_archivers.gtfsrt.modules

import dagger.Binds
import dagger.Module
import systems.choochoo.transit_data_archivers.core.configuration.ConfigurationCore
import systems.choochoo.transit_data_archivers.gtfsrt.Configuration

@Module
internal abstract class ConfigurationModule {
    @Binds
    abstract fun configuration(configuration: Configuration): ConfigurationCore
}