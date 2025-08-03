@file:OptIn(ExperimentalTime::class)

package systems.choochoo.transit_data_archivers.gtfsrt.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.google.common.collect.ListMultimap
import com.google.transit.realtime.GtfsRealtime.FeedMessage
import okhttp3.Protocol
import systems.choochoo.transit_data_archivers.common.utils.EpochSecondInstant
import systems.choochoo.transit_data_archivers.gtfsrt.extensions.GtfsRealtimeExtension
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal enum class FetchStatus {
    ERROR,
    SUCCESS,
    UNCHANGED,
    NOT_MODIFIED
}

@JsonNaming(SnakeCaseStrategy::class)
internal data class FeedContents(
    @get:JsonIgnore
    @set:JsonIgnore
    var status: FetchStatus,
    var producer: String,
    var feed: String,
    @get:JsonSerialize(using = EpochSecondInstant.Serializer::class)
    @set:JsonDeserialize(using = EpochSecondInstant.Deserializer::class)
    var fetchTime: Instant,
    var errorMessage: String? = null,
    var statusCode: Int? = null,
    var statusMessage: String? = null,
    var protocol: Protocol? = null,
    var responseHeaders: ListMultimap<String, String>? = null,
    var responseTimeMillis: Int? = null,
    var responseBodyLength: Int? = null,
    var enabledExtensions: Set<GtfsRealtimeExtension> = emptySet()
) {

    var isError: Boolean
        get() {
            return status == FetchStatus.ERROR
        }
        set(value) {
            status = if (value) {
                FetchStatus.ERROR
            } else {
                FetchStatus.SUCCESS
            }
        }

    @get:JsonProperty("response_body_b64")
    var responseBody: ByteArray? = null
    var responseContents: FeedMessage? = null

    @get:JsonIgnore
    @set:JsonIgnore
    var eTag: String? = null
    @get:JsonIgnore
    @set:JsonIgnore
    var lastModified: Instant? = null
    @get:JsonIgnore
    @set:JsonIgnore
    var headerTimestamp: Instant? = null
}
