package systems.choochoo.transit_data_archivers.common.configuration

import systems.choochoo.transit_data_archivers.common.utils.CompressionMode
import systems.choochoo.transit_data_archivers.common.utils.CompressionMode.*
import java.nio.file.Path

data class FallbackConfiguration (
    val enabled: Boolean = false,
    val basePath: Path? = null,
    val compression: CompressionMode = NONE,
)