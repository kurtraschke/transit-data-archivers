package systems.choochoo.transit_data_archivers.gtfsrt.dump

import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.google.protobuf.ExtensionRegistry
import com.google.transit.realtime.GtfsRealtime.FeedMessage
import com.hubspot.jackson.datatype.protobuf.ProtobufJacksonConfig
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import picocli.CommandLine
import picocli.CommandLine.*
import picocli.CommandLine.Model.CommandSpec
import systems.choochoo.transit_data_archivers.gtfsrt.dump.OutputFormat.PBTEXT
import systems.choochoo.transit_data_archivers.gtfsrt.dump.TimestampDisplay.LOCAL
import systems.choochoo.transit_data_archivers.gtfsrt.dump.TimestampDisplay.UTC
import systems.choochoo.transit_data_archivers.gtfsrt.extensions.GtfsRealtimeExtension
import java.net.URL
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
import java.util.*
import java.util.concurrent.Callable
import kotlin.io.path.readBytes
import kotlin.system.exitProcess


@Command(name = "gtfs-rt-dump", mixinStandardHelpOptions = true)
class GtfsRtDump : Callable<Int> {

    @Option(
        names = ["-O", "--output-format"],
        paramLabel = "OUTPUT_FORMAT",
        description = ["Output format to generate. Valid values: ${'$'}{COMPLETION-CANDIDATES}"]
    )
    private var outputFormat: OutputFormat = PBTEXT

    @Option(
        names = ["-T", "--timestamp-display"],
        paramLabel = "TIMESTAMP_FORMAT",
        description = ["Output format to generate. Valid values: ${'$'}{COMPLETION-CANDIDATES}"]
    )
    private var timestampDisplay: TimestampDisplay? = null

    @Option(
        names = ["-E", "--enable-extension"],
        paramLabel = "EXTENSION",
        description = ["GTFS-rt extension to enable. Valid values: ${'$'}{COMPLETION-CANDIDATES}"]
    )
    private var enabledExtensions: Set<GtfsRealtimeExtension> = emptySet()

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

        val fm = FeedMessage.parseFrom(input, registry)

        val out = when (outputFormat) {
            OutputFormat.JSON -> {
                val om = jsonMapper {
                    addModules(
                        kotlinModule(),
                        ProtobufModule(
                            ProtobufJacksonConfig.builder()
                                .extensionRegistry(registry)
                                .build()
                        )
                    )
                }

                om.writerWithDefaultPrettyPrinter().writeValueAsString(fm)
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

internal enum class OutputFormat {
    JSON,
    PBTEXT
}

internal enum class TimestampDisplay {
    LOCAL,
    UTC
}

internal class InputOptions {
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
            val ts = Instant.ofEpochSecond(m.toLong())

            val f = when (td) {
                LOCAL -> {
                    ISO_LOCAL_DATE_TIME.format(ts.atZone(TimeZone.getDefault().toZoneId()))
                }

                UTC -> {
                    ISO_LOCAL_DATE_TIME.withZone(ZoneId.of("UTC")).format(ts) + "Z"
                }
            }

            "${matchResult.value}\t/* $f */"
        }
    }
}