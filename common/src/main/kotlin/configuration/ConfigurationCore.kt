package systems.choochoo.transit_data_archivers.common.configuration

import kotlin.time.Duration

interface ConfigurationCore {
    val database: DatabaseConfiguration
    val fallback: FallbackConfiguration
    val operatorContact: String?
    val callTimeout: Duration
}