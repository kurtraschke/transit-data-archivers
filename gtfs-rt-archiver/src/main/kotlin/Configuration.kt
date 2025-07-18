package systems.choochoo.transit_data_archivers.gtfsrt

import arrow.core.Option
import com.sksamuel.hoplite.Masked
import systems.choochoo.transit_data_archivers.common.configuration.CommonConfiguration
import systems.choochoo.transit_data_archivers.common.configuration.DatabaseConfiguration
import systems.choochoo.transit_data_archivers.common.configuration.FallbackConfiguration
import systems.choochoo.transit_data_archivers.common.configuration.FetchInterval
import systems.choochoo.transit_data_archivers.gtfsrt.extensions.GtfsRealtimeExtension
import java.net.URL
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal data class Configuration(
    override val database: DatabaseConfiguration = DatabaseConfiguration(),
    override val fallback: FallbackConfiguration = FallbackConfiguration(),
    val fetchInterval: FetchInterval = FetchInterval(30.seconds),
    override val callTimeout: Duration = 15.seconds,
    val storeResponseBody: Boolean = false,
    val storeResponseBodyOnError: Boolean = true,
    override val operatorContact: String?,
    val feeds: List<Feed>
): CommonConfiguration

internal data class Feed(
    val producer: String,
    val feed: String,
    val feedUrl: URL,
    val fetchInterval: Option<FetchInterval>, //https://github.com/sksamuel/hoplite/issues/453
    val storeResponseBody: Boolean?,
    val storeResponseBodyOnError: Boolean?,
    val headers: Map<String, String> = emptyMap(),
    val basicAuthCredentials: BasicAuthCredential?,
    val ignoreTLSErrors: Boolean = false,
    val queryParameters: Map<String, String> = emptyMap(),
    val extensions: Set<GtfsRealtimeExtension> = emptySet()
)

internal data class BasicAuthCredential(val username: String, val password: Masked)