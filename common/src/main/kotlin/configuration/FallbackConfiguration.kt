package systems.choochoo.transit_data_archivers.common.configuration

import systems.choochoo.transit_data_archivers.common.utils.CompressionMode
import systems.choochoo.transit_data_archivers.common.utils.CompressionMode.NONE
import java.nio.file.Path

data class FallbackConfiguration(
    val enabled: Boolean = false,
    val basePath: Path? = null,
    val compression: CompressionMode = NONE,
) {
    init {
        require((!enabled) || basePath != null) { "Fallback base path must not be null when fallback writer is enabled" }
    }
}