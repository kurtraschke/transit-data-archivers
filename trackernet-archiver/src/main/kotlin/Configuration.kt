package systems.choochoo.transit_data_archivers.trackernet

import com.sksamuel.hoplite.Masked
import systems.choochoo.transit_data_archivers.common.configuration.CommonConfiguration
import systems.choochoo.transit_data_archivers.common.configuration.DatabaseConfiguration
import systems.choochoo.transit_data_archivers.common.configuration.FallbackConfiguration
import systems.choochoo.transit_data_archivers.common.configuration.FetchInterval
import java.net.URI
import java.net.URL
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@JvmInline
value class DerateFactor(val v: Double) {
    init {
        require (v in  0.0..1.0) {
            "Derate factor must be between 0 and 1."
        }
    }
}

internal data class Configuration (
    override val database: DatabaseConfiguration = DatabaseConfiguration(),
    override val fallback: FallbackConfiguration = FallbackConfiguration(),
    val appKey: Masked,
    val baseUrl: URL = URI.create("https://api.tfl.gov.uk/TrackerNet/").toURL(),
    override val operatorContact: String?,
    val maxRequestsPerMinute: Int = 500,
    val derateFactor: DerateFactor = DerateFactor(0.8),
    override val callTimeout: Duration = 2.seconds,
    val lines: List<LineConfiguration>
) : CommonConfiguration

internal data class LineConfiguration(
    val lineCode: String,
    val fetchInterval: FetchInterval = FetchInterval(30.seconds),
    val excludedStations: List<String> = emptyList(),
)