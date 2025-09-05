package systems.choochoo.transit_data_archivers.gtfsrt.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.protobuf.ExtensionRegistry
import com.hubspot.jackson.datatype.protobuf.ProtobufJacksonConfig
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.binder.cache.GuavaCacheMetrics
import jakarta.inject.Inject
import jakarta.inject.Singleton
import systems.choochoo.transit_data_archivers.gtfsrt.extensions.GtfsRealtimeExtension
import kotlin.math.pow

@Singleton
internal class ObjectMapperCache @Inject constructor(registry: MeterRegistry){

    // We want to set a limit on the size of these caches, to prevent unbounded expansion in the event of a mishap.
    // But rather than setting an arbitrary limit, we want the number to be grounded in some basis.
    // The number of possible combinations of extensions is the cardinality of the powerset of the set of extensions,
    // or 2^n, where n is the number of extensions.

    @Suppress("PrivatePropertyName")
    private val CACHE_SIZE = 2.0.pow(GtfsRealtimeExtension.entries.size.toDouble()).toLong()

    private val extensionRegistryCache = CacheBuilder.newBuilder()
        .maximumSize(CACHE_SIZE)
        .recordStats()
        .build(CacheLoader.from { key: Set<GtfsRealtimeExtension> ->
            val registry = ExtensionRegistry.newInstance()
            key.forEach { it.registerExtension(registry) }
            registry
        })

    private val objectMapperCache = CacheBuilder.newBuilder()
        .maximumSize(CACHE_SIZE)
        .recordStats()
        .build(CacheLoader.from { key: Set<GtfsRealtimeExtension> ->
            val registry = extensionRegistryCache.get(key)

            val config = ProtobufJacksonConfig.builder()
                .extensionRegistry(registry)
                .build()

            jsonMapper {
                addModules(
                    kotlinModule(),
                    GuavaModule(),
                    JavaTimeModule(),
                    ProtobufModule(config)
                )
            }
        })

    init {
        GuavaCacheMetrics.monitor(registry, extensionRegistryCache, "registryCache", Tags.empty())
        GuavaCacheMetrics.monitor(registry, objectMapperCache, "objectMapperCache", Tags.empty())
    }

    fun getExtensionRegistry(extensions: Set<GtfsRealtimeExtension>): ExtensionRegistry = extensionRegistryCache.get(extensions)
    fun getObjectMapper(extensions: Set<GtfsRealtimeExtension>) : ObjectMapper = objectMapperCache.get(extensions)
}