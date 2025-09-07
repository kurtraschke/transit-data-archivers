package systems.choochoo.transit_data_archivers.query_proxy.modules

import dagger.Binds
import dagger.Module
import systems.choochoo.transit_data_archivers.common.configuration.HasDatabaseConfiguration
import systems.choochoo.transit_data_archivers.common.configuration.HasOperatorContact
import systems.choochoo.transit_data_archivers.query_proxy.Configuration

@Module
internal abstract class ConfigurationModule {
    @Binds
    abstract fun databaseConfiguration(configuration: Configuration): HasDatabaseConfiguration

    @Binds
    abstract fun operatorContact(configuration: Configuration): HasOperatorContact
}