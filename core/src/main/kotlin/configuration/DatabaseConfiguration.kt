package systems.choochoo.transit_data_archivers.core.configuration

import com.sksamuel.hoplite.Masked
import java.net.URI

data class DatabaseConfiguration(
    val url: URI = URI.create("http://localhost:8123"),
    val username: String = "default",
    val password: Masked = Masked(""),
    val options: Map<String, String> = emptyMap()
)