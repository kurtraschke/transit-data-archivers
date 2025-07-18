@file:OptIn(ExperimentalTime::class)

package systems.choochoo.transit_data_archivers.gtfsrt.jobs

import com.clickhouse.client.api.Client
import com.clickhouse.client.api.ClientException
import com.clickhouse.data.ClickHouseFormat.JSONEachRow
import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.google.common.base.Stopwatch
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.collect.ImmutableListMultimap
import com.google.common.net.HttpHeaders.*
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.InvalidProtocolBufferException
import com.google.transit.realtime.GtfsRealtime.FeedMessage
import com.hubspot.jackson.datatype.protobuf.ProtobufJacksonConfig
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import io.github.oshai.kotlinlogging.KotlinLogging
import io.prometheus.metrics.core.metrics.Counter
import io.prometheus.metrics.core.metrics.Gauge
import io.prometheus.metrics.core.metrics.Histogram
import io.prometheus.metrics.model.snapshots.Unit
import jakarta.inject.Inject
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.quartz.*
import org.slf4j.MDC
import systems.choochoo.transit_data_archivers.common.utils.FallbackWriter
import systems.choochoo.transit_data_archivers.common.utils.ignoreAllTLSErrors
import systems.choochoo.transit_data_archivers.gtfsrt.Feed
import systems.choochoo.transit_data_archivers.gtfsrt.cacheMetrics
import systems.choochoo.transit_data_archivers.gtfsrt.entities.FeedContents
import systems.choochoo.transit_data_archivers.gtfsrt.entities.FetchStatus
import systems.choochoo.transit_data_archivers.gtfsrt.entities.FetchStatus.*
import systems.choochoo.transit_data_archivers.gtfsrt.extensions.GtfsRealtimeExtension
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection.HTTP_NOT_MODIFIED
import java.util.*
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.time.*
import kotlin.time.Duration.Companion.milliseconds
import java.lang.Boolean as jBoolean

const val LAST_ETAG = "lastETag"
const val LAST_LAST_MODIFIED = "lastLastModified"
const val LAST_HEADER_TIMESTAMP = "lastHeaderTimestamp"

private val STATUSES_TO_PERSIST: EnumSet<FetchStatus> = EnumSet.of(SUCCESS, ERROR)

private val fetchCount = Counter.builder()
    .name("fetch_event_total")
    .help("number of fetch events")
    .labelNames("producer", "feed", "fetch_status")
    .register()

private val uncaughtErrorCount = Counter.builder()
    .name("uncaught_exception_total")
    .help("number of uncaught exceptions")
    .labelNames("producer", "feed")
    .register()

private val fallbackArchiveCount = Counter.builder()
    .name("fallback_archive_count")
    .help("number of fetches written to fallback destination")
    .labelNames("producer", "feed")
    .register()

private val lastFetchTime = Gauge.builder()
    .name("last_fetch_time")
    .help("last fetch time as epoch timestamp")
    .labelNames("producer", "feed", "fetch_status")
    .unit(Unit.SECONDS)
    .register()

private val totalFetchDuration = Histogram.builder()
    .name("overall_fetch_duration")
    .help("overall fetch duration in seconds")
    .labelNames("producer", "feed", "fetch_status")
    .unit(Unit.SECONDS)
    .register()

private val serverResponseDuration = Histogram.builder()
    .name("server_response_duration")
    .help("time for remote server to respond in seconds")
    .labelNames("producer", "feed", "fetch_status")
    .unit(Unit.SECONDS)
    .register()

private val responseSizeBytes = Histogram.builder()
    .name("response_size_bytes")
    .help("uncompressed size of response")
    .labelNames("producer", "feed", "fetch_status")
    .unit(Unit.BYTES)
    .register()

// We want to set some limit on the size of these caches, to prevent unbounded expansion in the event of a mishap.
// But rather than setting an arbitrary limit, we want the number to be grounded in some basis.
// The number of possible combinations of extensions is the cardinality of the powerset of the set of extensions,
// or 2^n, where n is the number of extensions.

private val CACHE_SIZE = 2.0.pow(GtfsRealtimeExtension.entries.size.toDouble()).roundToLong()

private val registryCache = CacheBuilder.newBuilder()
    .maximumSize(CACHE_SIZE)
    .recordStats()
    .build(CacheLoader.from { key: Set<GtfsRealtimeExtension> ->
        val registry = ExtensionRegistry.newInstance()
        key.forEach { it.registerExtension(registry) }
        registry
    })
    .apply { cacheMetrics.addCache("registryCache", this) }

private val objectMapperCache = CacheBuilder.newBuilder()
    .maximumSize(CACHE_SIZE)
    .recordStats()
    .build(CacheLoader.from { key: Set<GtfsRealtimeExtension> ->
        val registry = registryCache.get(key)

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
    .apply { cacheMetrics.addCache("objectMapperCache", this) }


private val log = KotlinLogging.logger {}

@DisallowConcurrentExecution
@PersistJobDataAfterExecution
internal class FeedArchiveJob : Job {

    @Inject
    lateinit var httpClient: OkHttpClient

    @Inject
    lateinit var clickHouseClient: Client

    @Inject
    lateinit var fallbackWriter: FallbackWriter

    lateinit var feed: Feed

    lateinit var storeResponseBody: jBoolean
    lateinit var storeResponseBodyOnError: jBoolean

    var lastETag: String? = null
    var lastLastModified: Instant? = null
    var lastHeaderTimestamp: Instant? = null


    override fun execute(context: JobExecutionContext) {
        val sw = Stopwatch.createStarted()

        try {
            MDC.put("producer", feed.producer)
            MDC.put("feed", feed.feed)

            val fetchTime = context.fireTime.toInstant().toKotlinInstant()

            log.debug { "Beginning fetch at $fetchTime" }

            val feedUrlBuilder = feed.feedUrl.toHttpUrlOrNull()!!.newBuilder()
            feed.queryParameters.forEach(feedUrlBuilder::addQueryParameter)

            val headersBuilder = feed.headers.toHeaders().newBuilder()

            val conditionalGet = lastETag != null || lastLastModified != null

            lastETag?.let {
                headersBuilder[IF_NONE_MATCH] = it
            }

            lastLastModified?.let {
                if (it <= fetchTime) {
                    headersBuilder[IF_MODIFIED_SINCE] = it.toJavaInstant()
                }
            }

            val request = Request(feedUrlBuilder.build(), headersBuilder.build())

            val customizedClient = httpClient
                .newBuilder()
                .apply {
                    if (feed.ignoreTLSErrors) {
                        ignoreAllTLSErrors()
                    }

                    feed.basicAuthCredentials?.let {
                        authenticator(Authenticator { _, response ->
                            if (response.request.header(AUTHORIZATION) != null) {
                                return@Authenticator null
                            }

                            val credential = Credentials.basic(it.username, it.password.toString())
                            response.request.newBuilder().header(AUTHORIZATION, credential).build()
                        })
                    }
                }.build()

            val fc = FeedContents(
                SUCCESS,
                feed.producer,
                feed.feed,
                fetchTime,
                enabledExtensions = feed.extensions
            )

            try {
                customizedClient
                    .newCall(request)
                    .execute()
                    .use { response ->
                        fc.status = if (conditionalGet && response.code == HTTP_NOT_MODIFIED) {
                            log.debug { "Conditional GET returned 304" }
                            NOT_MODIFIED
                        } else if (response.isSuccessful) {
                            SUCCESS
                        } else {
                            ERROR
                        }

                        fc.statusCode = response.code
                        fc.statusMessage = response.message
                        fc.protocol = response.protocol

                        fc.responseHeaders = response
                            .headers
                            .toMultimap()
                            .entries
                            .stream()
                            .collect(
                                ImmutableListMultimap.flatteningToImmutableListMultimap(
                                    { it.key },
                                    { it.value.stream() })
                            )

                        fc.responseTimeMillis =
                            (response.receivedResponseAtMillis - response.sentRequestAtMillis).toInt()
                        val responseBodyBytes = response.body.bytes()

                        fc.responseBodyLength = responseBodyBytes.size

                        fc.lastModified = response.headers.getInstant(LAST_MODIFIED)?.toKotlinInstant()
                        fc.eTag = response.headers[ETAG]

                        if (fc.status == SUCCESS) {
                            try {
                                val fm = FeedMessage.parseFrom(responseBodyBytes, registryCache.get(feed.extensions))

                                fc.headerTimestamp = Instant.fromEpochSeconds(fm.header.timestamp)
                                fc.responseContents = fm

                                if (lastHeaderTimestamp != null &&
                                    (lastHeaderTimestamp!! <= fetchTime
                                            && fc.headerTimestamp!! <= lastHeaderTimestamp!!)
                                ) {
                                    fc.status = UNCHANGED
                                    log.debug { "GTFS-rt header timestamp unchanged since last fetch" }
                                }
                            } catch (e: InvalidProtocolBufferException) {
                                log.warn(e) { "Protobuf parsing failed" }

                                fc.status = ERROR
                                fc.errorMessage = e.message
                            }
                        }

                        if (storeResponseBody.booleanValue() || (storeResponseBodyOnError.booleanValue() && fc.status == ERROR)) {
                            fc.responseBody = responseBodyBytes
                        }
                    }
            } catch (ie: IOException) {
                log.warn(ie) { "IOException during feed fetch" }
                fc.status = ERROR
                fc.errorMessage = ie.message
            }

            if (STATUSES_TO_PERSIST.contains(fc.status)) {
                val s = objectMapperCache.get(feed.extensions)
                    .writeValueAsBytes(fc)

                try {
                    val r = ByteArrayInputStream(s)
                        .use {
                            clickHouseClient.insert(
                                "feed_contents",
                                listOf(
                                    "producer",
                                    "feed",
                                    "fetch_time",
                                    "is_error",
                                    "error_message",
                                    "response_time_millis",
                                    "status_code",
                                    "status_message",
                                    "protocol",
                                    "response_headers",
                                    "response_body_b64",
                                    "response_body_length",
                                    "response_contents",
                                    "enabled_extensions"
                                ),
                                it,
                                JSONEachRow
                            )
                        }

                    val rows = r.get().writtenRows

                    log.trace { "Wrote $rows rows" }
                } catch (e: ClientException) {
                    log.warn(e) { "Exception while persisting to database; will attempt fallback write" }

                    fallbackArchiveCount.labelValues(fc.producer, fc.feed).inc()

                    fallbackWriter.write(
                        linkedMapOf("producer" to fc.producer, "feed" to fc.feed),
                        fc.fetchTime,
                        s
                    )
                }
            }

            if (fc.status == SUCCESS) {
                context.jobDetail.jobDataMap[LAST_ETAG] = fc.eTag
                context.jobDetail.jobDataMap[LAST_LAST_MODIFIED] = fc.lastModified
                context.jobDetail.jobDataMap[LAST_HEADER_TIMESTAMP] = fc.headerTimestamp
            }

            sw.stop()
            log.trace { "Fetch took $sw" }
            log.trace { fc.toString() }

            fetchCount.labelValues(fc.producer, fc.feed, fc.status.toString()).inc()
            lastFetchTime.labelValues(fc.producer, fc.feed, fc.status.toString())
                .set(fc.fetchTime.epochSeconds.toDouble())
            totalFetchDuration.labelValues(fc.producer, fc.feed, fc.status.toString())
                .observe(sw.elapsed().toKotlinDuration().toDouble(DurationUnit.SECONDS))

            fc.responseTimeMillis?.let {
                serverResponseDuration.labelValues(fc.producer, fc.feed, fc.status.toString())
                    .observe(it.milliseconds.toDouble(DurationUnit.SECONDS))
            }

            fc.responseBodyLength?.let {
                responseSizeBytes.labelValues(fc.producer, fc.feed, fc.status.toString())
                    .observe(it.toDouble())
            }

            context.result = fc
        } catch (e: Exception) {
            log.error(e) { "Uncaught exception during feed fetch" }
            uncaughtErrorCount.labelValues(feed.producer, feed.feed).inc()
            throw JobExecutionException(e)
        } finally {
            MDC.clear()
        }
    }
}