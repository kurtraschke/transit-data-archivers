package systems.choochoo.transit_data_archivers.core.utils

import systems.choochoo.transit_data_archivers.core.configuration.ApplicationVersion

fun constructUserAgentString(
    applicationVersion: ApplicationVersion,
    operatorContact: String? = null,
    okHttpVersion: String? = null
): String {
    val userAgentString =
        "${applicationVersion.groupId}:${applicationVersion.artifactId}/${applicationVersion.version}" +
                " (${applicationVersion.commitId}; ${applicationVersion.branch})" +
                (okHttpVersion?.let { " (okhttp/${it})" } ?: "") +
                (operatorContact?.let { " ($it)" } ?: "")

    return userAgentString
}