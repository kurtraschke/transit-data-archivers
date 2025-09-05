package systems.choochoo.transit_data_archivers.common.modules

import dagger.Binds
import dagger.Module
import dagger.Provides
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import jakarta.inject.Singleton

@Module
abstract class MicrometerModule {
    @Suppress("unused")
    @Binds
    abstract fun meterRegistry(prometheusMeterRegistry: PrometheusMeterRegistry): MeterRegistry

    companion object {
        @Provides
        @Singleton
        fun providePrometheusMeterRegistry() = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    }
}