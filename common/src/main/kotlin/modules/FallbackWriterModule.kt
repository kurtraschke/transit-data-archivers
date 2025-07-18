package systems.choochoo.transit_data_archivers.common.modules

import dagger.Module
import dagger.Provides
import jakarta.inject.Singleton
import systems.choochoo.transit_data_archivers.common.configuration.ConfigurationCore
import systems.choochoo.transit_data_archivers.common.utils.DummyFallbackWriter
import systems.choochoo.transit_data_archivers.common.utils.FallbackWriter
import systems.choochoo.transit_data_archivers.common.utils.LocalPathFallbackWriter

@Module
class FallbackWriterModule {
    companion object {
        @Provides
        @Singleton
        fun provideFallbackWriter(configuration: ConfigurationCore): FallbackWriter =
            if (!configuration.fallback.enabled) {
                DummyFallbackWriter()
            } else {
                LocalPathFallbackWriter(
                    configuration.fallback.basePath!!,
                    configuration.fallback.compression
                )
            }
    }
}