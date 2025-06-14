package systems.choochoo.transit_data_archivers.core.configuration

import com.sksamuel.hoplite.Masked
import java.net.URI

data class DatabaseConfiguration(
    val url: URI,
    val username: String? = "default",
    val password: Masked?,
    val options: Map<String, String> = emptyMap()
)