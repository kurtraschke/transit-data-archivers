@file:OptIn(ExperimentalTime::class)

package systems.choochoo.transit_data_archivers.gtfsrt.jobs

import com.clickhouse.client.api.ClickHouseException
import com.clickhouse.client.api.Client
import com.clickhouse.data.ClickHouseFormat.JSONEachRow
import com.google.common.base.Stopwatch
import com.google.common.collect.ImmutableListMultimap
import com.google.common.net.HttpHeaders.*
import com.google.protobuf.ExtensionRegistryLite
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.Parser
import com.google.transit.realtime.GtfsRealtime.FeedMessage
import io.github.oshai.kotlinlogging.KotlinLogging
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
import systems.choochoo.transit_data_archivers.gtfsrt.entities.FeedContents
import systems.choochoo.transit_data_archivers.gtfsrt.entities.FetchStatus
import systems.choochoo.transit_data_archivers.gtfsrt.entities.FetchStatus.*
import systems.choochoo.transit_data_archivers.gtfsrt.utils.Metrics
import systems.choochoo.transit_data_archivers.gtfsrt.utils.ObjectMapperCache
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection.HTTP_NOT_MODIFIED
import java.util.*
import kotlin.time.*
import kotlin.time.Duration.Companion.milliseconds
import java.lang.Boolean as jBoolean

const val LAST_ETAG = "lastETag"
const val LAST_LAST_MODIFIED = "lastLastModified"
const val LAST_HEADER_TIMESTAMP = "lastHeaderTimestamp"

private val STATUSES_TO_PERSIST: EnumSet<FetchStatus> = EnumSet.of(SUCCESS, ERROR)

private val log = KotlinLogging.logger {}

@DisallowConcurrentExecution
@PersistJobDataAfterExecution
internal class FeedArchiveJob : Job {

    @Inject
    lateinit var httpClient: OkHttpClient

    @Inject
    lateinit var objectMapperCache: ObjectMapperCache

    @Inject
    lateinit var clickHouseClient: Client

    @Inject
    lateinit var fallbackWriter: FallbackWriter

    @Inject
    lateinit var metrics: Metrics

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
                                val parseFunction: (Parser<FeedMessage>, ByteArray, ExtensionRegistryLite) -> FeedMessage = if (feed.parsePartial) {
                                    Parser<FeedMessage>::parsePartialFrom
                                } else {
                                    Parser<FeedMessage>::parseFrom
                                }

                                val fm = parseFunction(FeedMessage.parser(), responseBodyBytes, objectMapperCache.getExtensionRegistry(feed.extensions))

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
                val s = objectMapperCache.getObjectMapper(feed.extensions)
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
                } catch (e: ClickHouseException) {
                    log.warn(e) { "Exception while persisting to database; will attempt fallback write" }

                    metrics.fallbackArchiveCount.labelValues(fc.producer, fc.feed).inc()

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

            metrics.fetchCount.labelValues(fc.producer, fc.feed, fc.status.toString()).inc()
            metrics.lastFetchTime.labelValues(fc.producer, fc.feed, fc.status.toString())
                .set(fc.fetchTime.epochSeconds.toDouble())
            metrics.totalFetchDuration.labelValues(fc.producer, fc.feed, fc.status.toString())
                .observe(sw.elapsed().toKotlinDuration().toDouble(DurationUnit.SECONDS))

            fc.responseTimeMillis?.let {
                metrics.serverResponseDuration.labelValues(fc.producer, fc.feed, fc.status.toString())
                    .observe(it.milliseconds.toDouble(DurationUnit.SECONDS))
            }

            fc.responseBodyLength?.let {
                metrics.responseSizeBytes.labelValues(fc.producer, fc.feed, fc.status.toString())
                    .observe(it.toDouble())
            }

            context.result = fc
        } catch (e: Exception) {
            log.error(e) { "Uncaught exception during feed fetch" }
            metrics.uncaughtErrorCount.labelValues(feed.producer, feed.feed).inc()
            throw JobExecutionException(e)
        } finally {
            MDC.clear()
        }
    }
}