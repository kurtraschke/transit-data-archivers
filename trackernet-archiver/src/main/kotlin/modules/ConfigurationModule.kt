package systems.choochoo.transit_data_archivers.trackernet.modules

import dagger.Binds
import dagger.Module
import systems.choochoo.transit_data_archivers.common.configuration.HasCallTimeout
import systems.choochoo.transit_data_archivers.common.configuration.HasDatabaseConfiguration
import systems.choochoo.transit_data_archivers.common.configuration.HasFallbackConfiguration
import systems.choochoo.transit_data_archivers.common.configuration.HasOperatorContact
import systems.choochoo.transit_data_archivers.trackernet.Configuration

@Module
internal abstract class ConfigurationModule {
    @Binds
    abstract fun databaseConfiguration(configuration: Configuration): HasDatabaseConfiguration

    @Binds
    abstract fun fallbackConfiguration(configuration: Configuration): HasFallbackConfiguration

    @Binds
    abstract fun operatorContact(configuration: Configuration): HasOperatorContact

    @Binds
    abstract fun callTimeout(configuration: Configuration): HasCallTimeout
}