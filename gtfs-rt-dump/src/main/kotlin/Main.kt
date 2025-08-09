@file:OptIn(ExperimentalTime::class)

package systems.choochoo.transit_data_archivers.gtfsrt.dump

import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.ExtensionRegistryLite
import com.google.protobuf.Parser
import com.google.transit.realtime.GtfsRealtime.FeedMessage
import com.hubspot.jackson.datatype.protobuf.ProtobufJacksonConfig
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import picocli.CommandLine
import picocli.CommandLine.*
import systems.choochoo.transit_data_archivers.common.utils.VersionProvider
import systems.choochoo.transit_data_archivers.gtfsrt.dump.OutputFormat.PBTEXT
import systems.choochoo.transit_data_archivers.gtfsrt.extensions.GtfsRealtimeExtension
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.reflect.jvm.javaMethod
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


@Command(
    name = "gtfs-rt-dump",
    description = ["Utility to dump GTFS-rt feeds to Protocol Buffer text format or JSON"],
    mixinStandardHelpOptions = true,
    usageHelpAutoWidth = true,
    versionProvider = VersionProvider::class
)
internal fun dump(
    @Option(
        names = ["-O", "--output-format"],
        paramLabel = "<output format>",
        description = [$$"Output format to generate (defaults to ${DEFAULT-VALUE}). Valid values: ${COMPLETION-CANDIDATES}"],
        defaultValue = "PBTEXT"
    )
    outputFormat: OutputFormat,

    @Option(
        names = ["-T", "--timestamp-display"],
        paramLabel = "<timestamp format>",
        description = [$$"Time zone for timestamp enrichment. Valid values: ${COMPLETION-CANDIDATES}"]
    )
    timestampDisplay: TimestampDisplay?,

    @Option(
        names = ["-E", "--enable-extension"],
        paramLabel = "<extension>",
        description = [$$"GTFS-rt extension to enable. Valid values: ${COMPLETION-CANDIDATES}"]
    )
    enabledExtensions: Set<GtfsRealtimeExtension>?,

    @Option(
        names = ["-P", "--partial"],
        description = ["Enable partial parsing of malformed Protocol Buffer messages"]
    )
    partial: Boolean = false,

    @ArgGroup(exclusive = true)
    inputOptions: InputOptions?,
): Int {
    val input = when {
        inputOptions?.inputFile != null -> inputOptions.inputFile.readBytes()
        inputOptions?.inputUrl != null -> inputOptions.inputUrl.readBytes()
        else -> System.`in`.readBytes()
    }

    val registry = ExtensionRegistry.newInstance()
    enabledExtensions?.forEach { it.registerExtension(registry) }

    val parser = FeedMessage.parser()

    val parseFunction: (Parser<FeedMessage>, ByteArray, ExtensionRegistryLite) -> FeedMessage = if (partial) {
        Parser<FeedMessage>::parsePartialFrom
    } else {
        Parser<FeedMessage>::parseFrom
    }

    val fm = parseFunction(parser, input, registry)

    val out = when (outputFormat) {
        OutputFormat.JSON -> {
            jsonMapper {
                addModules(
                    kotlinModule(),
                    ProtobufModule(
                        ProtobufJacksonConfig.builder()
                            .extensionRegistry(registry)
                            .build()
                    )
                )
            }
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(fm)
        }

        PBTEXT -> fm.toString()
    }

    println(timestampDisplay?.enrichTimestamps(out) ?: out)

    return ExitCode.OK
}


fun main(args: Array<String>): Unit =
    exitProcess(CommandLine(::dump.javaMethod).setCaseInsensitiveEnumValuesAllowed(true).execute(*args))

internal enum class OutputFormat {
    JSON,
    PBTEXT
}

@Suppress("unused")
internal enum class TimestampDisplay(private val tz: TimeZone) {
    LOCAL(TimeZone.currentSystemDefault()),
    UTC(TimeZone.UTC);

    fun enrichTimestamps(out: String): String {
        return TIMESTAMP_PATTERN.replace(out) { matchResult ->
            val (m) = matchResult.destructured

            if (m == "" || m == "0") {
                matchResult.value
            } else {
                val f = Instant
                    .fromEpochSeconds(m.toLong())
                    .toLocalDateTime(this.tz)
                    .toString()

                "${matchResult.value}\t/* $f */"
            }
        }
    }

    companion object {
        private val TIMESTAMP_PATTERN = Regex(""""?(?:start|end|time(?:stamp)?|(?:updated|created)_at)"? ?: (\d+)""")
    }
}

internal class InputOptions {
    @Option(names = ["-F", "--file"], paramLabel = "<file>", description = ["Path to GTFS-rt file to read."])
    val inputFile: Path? = null

    @Option(names = ["-U", "--url"], paramLabel = "<url>", description = ["URL of GTFS-rt feed to fetch."])
    val inputUrl: URL? = null
}