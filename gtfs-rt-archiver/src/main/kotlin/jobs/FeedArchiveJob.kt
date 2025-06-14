package systems.choochoo.transit_data_archivers.gtfsrt.jobs

import com.clickhouse.client.api.Client
import com.clickhouse.data.ClickHouseFormat.Parquet
import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.google.common.base.Stopwatch
import com.google.common.collect.ImmutableListMultimap
import com.google.common.net.HttpHeaders.*
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.InvalidProtocolBufferException
import com.google.transit.realtime.GtfsRealtime.FeedMessage
import com.hubspot.jackson.datatype.protobuf.ProtobufJacksonConfig
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import com.jerolba.carpet.CarpetWriter
import com.jerolba.carpet.ColumnNamingStrategy.SNAKE_CASE
import io.github.oshai.kotlinlogging.KotlinLogging
import io.prometheus.metrics.core.metrics.Counter
import io.prometheus.metrics.core.metrics.Gauge
import io.prometheus.metrics.core.metrics.Histogram
import io.prometheus.metrics.model.snapshots.Unit
import jakarta.inject.Inject
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.parquet.hadoop.metadata.CompressionCodecName.UNCOMPRESSED
import org.apache.parquet.io.api.Binary
import org.quartz.*
import org.slf4j.MDC
import systems.choochoo.transit_data_archivers.gtfsrt.Feed
import systems.choochoo.transit_data_archivers.gtfsrt.entities.FeedContents
import systems.choochoo.transit_data_archivers.gtfsrt.entities.FetchStatus
import systems.choochoo.transit_data_archivers.gtfsrt.entities.FetchStatus.*
import systems.choochoo.transit_data_archivers.gtfsrt.ignoreAllTLSErrors
import systems.choochoo.transit_data_archivers.model.FeedContentsRow
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection.HTTP_NOT_MODIFIED
import java.util.*
import kotlin.time.DurationUnit
import kotlin.time.toKotlinDuration
import java.lang.Boolean as jBoolean

const val LAST_ETAG = "lastETag"
const val LAST_LAST_MODIFIED = "lastLastModified"
const val LAST_HEADER_TIMESTAMP = "lastHeaderTimestamp"

private val STATUSES_TO_PERSIST: EnumSet<FetchStatus> = EnumSet.of(SUCCESS, ERROR)

private val fetchCount: Counter = Counter.builder()
    .name("fetch_event_total")
    .help("number of fetch events")
    .labelNames("producer", "feed", "fetch_status")
    .register()

private val uncaughtErrorCount: Counter = Counter.builder()
    .name("uncaught_exception_total")
    .help("number of uncaught exceptions")
    .labelNames("producer", "feed")
    .register()

private val lastFetchTime: Gauge = Gauge.builder()
    .name("last_fetch_time")
    .help("last fetch time as epoch timestamp")
    .labelNames("producer", "feed", "fetch_status")
    .unit(Unit.SECONDS)
    .register()

private val totalFetchDuration: Histogram = Histogram.builder()
    .name("overall_fetch_duration")
    .help("overall fetch duration in seconds")
    .labelNames("producer", "feed", "fetch_status")
    .unit(Unit.SECONDS)
    .register()

private val serverResponseDuration: Histogram = Histogram.builder()
    .name("server_response_duration")
    .help("time for remote server to respond in seconds")
    .labelNames("producer", "feed", "fetch_status")
    .unit(Unit.SECONDS)
    .register()

private val responseSizeBytes: Histogram = Histogram.builder()
    .name("response_size_bytes")
    .help("uncompressed size of response")
    .labelNames("producer", "feed", "fetch_status")
    .unit(Unit.BYTES)
    .register()

private val genericMapper = jsonMapper {
    addModules(
        kotlinModule(),
        GuavaModule(),
        JavaTimeModule()
    )
}

private val log = KotlinLogging.logger {}

@DisallowConcurrentExecution
@PersistJobDataAfterExecution
internal class FeedArchiveJob : Job {

    @Inject
    lateinit var httpClient: OkHttpClient

    @Inject
    lateinit var clickHouseClient: Client

    lateinit var feed: Feed

    lateinit var storeResponseBody: jBoolean
    lateinit var storeResponseBodyOnError: jBoolean

    var lastETag: String? = null
    var lastLastModified: Instant? = null
    var lastHeaderTimestamp: Instant? = null


    override fun execute(context: JobExecutionContext) {
        val sw = Stopwatch.createStarted()

        val registry = ExtensionRegistry.newInstance()
        feed.extensions.forEach { it.registerExtension(registry) }

        val config = ProtobufJacksonConfig.builder()
            .extensionRegistry(registry)
            .build()

        val om = genericMapper
            .rebuild()
            .addModules(ProtobufModule(config))
            .build()

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

            val request = Request.Builder()
                .url(feedUrlBuilder.build())
                .headers(headersBuilder.build())
                .build()

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
                fetchTime
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
                        val responseBodyBytes = response.body!!.bytes()

                        fc.responseBodyLength = responseBodyBytes.size

                        fc.lastModified = response.headers.getInstant(LAST_MODIFIED)?.toKotlinInstant()
                        fc.eTag = response.headers[ETAG]

                        if (fc.status == SUCCESS) {
                            try {
                                val fm = FeedMessage.parseFrom(responseBodyBytes, registry)

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
                val os = ByteArrayOutputStream()

                os.use {
                    CarpetWriter.Builder(it, FeedContentsRow::class.java)
                        .withColumnNamingStrategy(SNAKE_CASE)
                        .withCompressionCodec(UNCOMPRESSED)
                        .withBloomFilterEnabled(false)
                        .build()
                        .use { writer ->
                            writer.write(
                                FeedContentsRow(
                                    fc.producer,
                                    fc.feed,
                                    fc.fetchTime.toJavaInstant(),
                                    fc.isError,
                                    fc.errorMessage,
                                    fc.responseTimeMillis,
                                    fc.statusCode,
                                    fc.statusMessage,
                                    fc.protocol?.name,
                                    fc.responseHeaders?.let {
                                        om.writeValueAsString(fc.responseHeaders)
                                    },
                                    fc.responseBody?.let { Binary.fromReusedByteArray(it) },
                                    fc.responseBodyLength,
                                    fc.responseContents?.let { om.writeValueAsString(it) },
                                    feed.extensions.map { it.name }.toSet()
                                )
                            )
                        }
                }

                val data = os.toByteArray()

                val ins = ByteArrayInputStream(data)

                val r = ins.use {
                    clickHouseClient.insert("feed_contents", it, Parquet)
                }

                val rows = r.get().writtenRows

                log.trace { "Wrote $rows rows" }
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
                    .observe(it / 1000.0)
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

