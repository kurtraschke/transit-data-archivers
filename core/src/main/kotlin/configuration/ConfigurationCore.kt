package systems.choochoo.transit_data_archivers.core.configuration

import kotlin.time.Duration

interface ConfigurationCore {
    val database: DatabaseConfiguration
    val operatorContact: String?
    val callTimeout: Duration
}