package systems.choochoo.transit_data_archivers.common.configuration

import java.time.Instant

data class ApplicationVersion(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val commitId: String,
    val branch: String,
    val buildTimestamp: Instant
)