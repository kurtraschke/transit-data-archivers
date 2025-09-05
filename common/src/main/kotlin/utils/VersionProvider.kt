package systems.choochoo.transit_data_archivers.common.utils

import picocli.CommandLine.IVersionProvider
import systems.choochoo.transit_data_archivers.common.configuration.loadApplicationVersion

class VersionProvider : IVersionProvider {
    override fun getVersion(): Array<String> {
        val applicationVersion = loadApplicationVersion()

        return arrayOf(
            "${applicationVersion.artifactId} version ${applicationVersion.version}",
            "Built from ${applicationVersion.commitId} at ${applicationVersion.buildTimestamp}"
        )
    }
}