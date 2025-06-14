package systems.choochoo.transit_data_archivers.gtfsrt.entities

import com.google.common.collect.ListMultimap
import com.google.transit.realtime.GtfsRealtime.FeedMessage
import kotlinx.datetime.Instant
import okhttp3.Protocol

internal enum class FetchStatus {
    ERROR,
    SUCCESS,
    UNCHANGED,
    NOT_MODIFIED
}

internal data class FeedContents(
    var status: FetchStatus,
    var producer: String,
    var feed: String,
    var fetchTime: Instant,
    var errorMessage: String? = null,
    var statusCode: Int? = null,
    var statusMessage: String? = null,
    var protocol: Protocol? = null,
    var responseHeaders: ListMultimap<String, String>? = null,
    var responseTimeMillis: Int? = null,
    var responseBodyLength: Int? = null
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

    var responseBody: ByteArray? = null
    var responseContents: FeedMessage? = null

    var eTag: String? = null
    var lastModified: Instant? = null
    var headerTimestamp: Instant? = null
}
