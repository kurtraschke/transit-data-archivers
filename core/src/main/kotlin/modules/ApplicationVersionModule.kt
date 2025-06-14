@file:OptIn(ExperimentalHoplite::class)

package systems.choochoo.transit_data_archivers.core.modules

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addResourceSource
import dagger.Module
import dagger.Provides
import jakarta.inject.Singleton
import systems.choochoo.transit_data_archivers.core.configuration.ApplicationVersion

@Module
class ApplicationVersionModule {
    companion object {
        @Singleton
        @Provides
        fun providesApplicationVersion(): ApplicationVersion {
            val applicationVersion = ConfigLoaderBuilder.empty()
                .addDefaultDecoders()
                .addDefaultPreprocessors()
                .addDefaultNodeTransformers()
                .addDefaultParamMappers()
                .addDefaultParsers()
                .addResourceSource("/maven-version.properties")
                .withExplicitSealedTypes()
                .build()
                .loadConfigOrThrow<ApplicationVersion>()

            return applicationVersion
        }
    }
}