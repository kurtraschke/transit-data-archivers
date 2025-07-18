package systems.choochoo.transit_data_archivers.trackernet.modules

import dagger.Binds
import dagger.Module
import systems.choochoo.transit_data_archivers.common.configuration.ConfigurationCore
import systems.choochoo.transit_data_archivers.trackernet.Configuration

@Module
internal abstract class ConfigurationModule {
    @Binds
    abstract fun configuration(configuration: Configuration): ConfigurationCore
}