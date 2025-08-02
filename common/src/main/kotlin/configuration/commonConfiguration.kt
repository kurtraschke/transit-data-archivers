package systems.choochoo.transit_data_archivers.common.configuration

import kotlin.time.Duration

interface HasDatabaseConfiguration {
    val database: DatabaseConfiguration
}

interface HasFallbackConfiguration {
    val fallback: FallbackConfiguration
}

interface HasOperatorContact {
    val operatorContact: String?
}

interface HasCallTimeout {
    val callTimeout: Duration
}