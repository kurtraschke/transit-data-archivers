package systems.choochoo.transit_data_archivers.common.configuration

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@JvmInline
value class FetchInterval(val v: Duration) {
    init {
        require(v >= 30.seconds) {
            "Fetch interval must be at least 30 seconds."
        }
    }
}