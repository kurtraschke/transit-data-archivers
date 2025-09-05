package systems.choochoo.transit_data_archivers.gtfsrt.utils

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.core.metrics.Counter
import io.prometheus.metrics.core.metrics.Gauge
import io.prometheus.metrics.core.metrics.Histogram
import io.prometheus.metrics.model.snapshots.Unit
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
internal class Metrics @Inject constructor(pmr: PrometheusMeterRegistry) {
    private val registry = pmr.prometheusRegistry

    internal val fetchCount = Counter.builder()
        .name("fetch_event_total")
        .help("number of fetch events")
        .labelNames("producer", "feed", "fetch_status")
        .register(registry)

    internal val uncaughtErrorCount = Counter.builder()
        .name("uncaught_exception_total")
        .help("number of uncaught exceptions")
        .labelNames("producer", "feed")
        .register(registry)

    internal val fallbackArchiveCount = Counter.builder()
        .name("fallback_archive_count")
        .help("number of fetches written to fallback destination")
        .labelNames("producer", "feed")
        .register(registry)

    internal val lastFetchTime = Gauge.builder()
        .name("last_fetch_time")
        .help("last fetch time as epoch timestamp")
        .labelNames("producer", "feed", "fetch_status")
        .unit(Unit.SECONDS)
        .register(registry)

    internal val totalFetchDuration = Histogram.builder()
        .name("overall_fetch_duration")
        .help("overall fetch duration in seconds")
        .labelNames("producer", "feed", "fetch_status")
        .unit(Unit.SECONDS)
        .register(registry)

    internal val serverResponseDuration = Histogram.builder()
        .name("server_response_duration")
        .help("time for remote server to respond in seconds")
        .labelNames("producer", "feed", "fetch_status")
        .unit(Unit.SECONDS)
        .register(registry)

    internal val responseSizeBytes = Histogram.builder()
        .name("response_size_bytes")
        .help("uncompressed size of response")
        .labelNames("producer", "feed", "fetch_status")
        .unit(Unit.BYTES)
        .register()

}