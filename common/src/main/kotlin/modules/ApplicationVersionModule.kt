package systems.choochoo.transit_data_archivers.common.modules

import dagger.Module
import dagger.Provides
import jakarta.inject.Singleton
import systems.choochoo.transit_data_archivers.common.configuration.ApplicationVersion
import systems.choochoo.transit_data_archivers.common.configuration.loadApplicationVersion

@Module
class ApplicationVersionModule {
    companion object {
        @Singleton
        @Provides
        fun providesApplicationVersion(): ApplicationVersion = loadApplicationVersion()
    }
}