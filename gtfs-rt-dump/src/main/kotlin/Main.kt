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
import picocli.CommandLine.Model.CommandSpec
import systems.choochoo.transit_data_archivers.gtfsrt.dump.OutputFormat.PBTEXT
import systems.choochoo.transit_data_archivers.gtfsrt.extensions.GtfsRealtimeExtension
import java.net.URL
import java.nio.file.Path
import java.util.concurrent.Callable
import kotlin.io.path.readBytes
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


@Command(name = "gtfs-rt-dump", mixinStandardHelpOptions = true)
private class GtfsRtDump : Callable<Int> {

    @Option(
        names = ["-O", "--output-format"],
        paramLabel = "OUTPUT_FORMAT",
        description = [$$"Output format to generate. Valid values: ${COMPLETION-CANDIDATES}"]
    )
    private var outputFormat: OutputFormat = PBTEXT

    @Option(
        names = ["-T", "--timestamp-display"],
        paramLabel = "TIMESTAMP_FORMAT",
        description = [$$"Output format to generate. Valid values: ${COMPLETION-CANDIDATES}"]
    )
    private var timestampDisplay: TimestampDisplay? = null

    @Option(
        names = ["-E", "--enable-extension"],
        paramLabel = "EXTENSION",
        description = [$$"GTFS-rt extension to enable. Valid values: ${COMPLETION-CANDIDATES}"]
    )
    private var enabledExtensions: Set<GtfsRealtimeExtension> = emptySet()

    @Option(
        names = ["-P", "--partial"],
        description = ["Enable partial parsing of malformed Protocol Buffer messages"]
    )
    private var partial: Boolean = false

    @ArgGroup(exclusive = true)
    private var inputOptions: InputOptions = InputOptions()

    @Spec
    private lateinit var spec: CommandSpec

    override fun call(): Int {
        val input = when {
            inputOptions.inputFile != null -> inputOptions.inputFile!!.readBytes()
            inputOptions.inputUrl != null -> inputOptions.inputUrl!!.readBytes()
            else -> System.`in`.readBytes()
        }

        val registry = ExtensionRegistry.newInstance()
        enabledExtensions.forEach { it.registerExtension(registry) }

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

        spec.commandLine().out.println(
            timestampDisplay?.let {
                enrichTimestamps(out, it)
            } ?: out
        )

        return ExitCode.OK
    }
}

fun main(args: Array<String>): Unit =
    exitProcess(CommandLine(GtfsRtDump()).setCaseInsensitiveEnumValuesAllowed(true).execute(*args))

private enum class OutputFormat {
    JSON,
    PBTEXT
}

@Suppress("unused")
private enum class TimestampDisplay(val tz: TimeZone) {
    LOCAL(TimeZone.currentSystemDefault()),
    UTC(TimeZone.UTC),
}

private class InputOptions {
    @Option(names = ["-F", "--file"], paramLabel = "FILE", description = ["Path to GTFS-rt file to read."])
    val inputFile: Path? = null

    @Option(names = ["-U", "--url"], paramLabel = "URL", description = ["URL of GTFS-rt feed to fetch."])
    val inputUrl: URL? = null
}

private val TIMESTAMP_PATTERN = Regex(""""?(?:start|end|time(?:stamp)?|(?:updated|created)_at)"? ?: (\d+)""")

private fun enrichTimestamps(out: String, td: TimestampDisplay): String {
    return TIMESTAMP_PATTERN.replace(out) { matchResult ->
        val (m) = matchResult.destructured

        if (m == "" || m == "0") {
            matchResult.value
        } else {
            val f = Instant
                .fromEpochSeconds(m.toLong())
                .toLocalDateTime(td.tz)
                .toString()

            "${matchResult.value}\t/* $f */"
        }
    }
}