package systems.choochoo.transit_data_archivers.udf

import com.fasterxml.jackson.databind.MappingIterator
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.TextFormat
import com.google.transit.realtime.GtfsRealtime.FeedMessage
import com.hubspot.jackson.datatype.protobuf.ProtobufJacksonConfig
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import picocli.CommandLine
import picocli.CommandLine.*
import picocli.CommandLine.Model.CommandSpec
import systems.choochoo.transit_data_archivers.common.utils.VersionProvider
import systems.choochoo.transit_data_archivers.gtfsrt.extensions.GtfsRealtimeExtension
import java.io.File
import java.io.InputStream
import java.util.concurrent.Callable
import kotlin.io.encoding.Base64
import kotlin.system.exitProcess


private val om = jsonMapper {
    addModule(kotlinModule())
}

@Command(
    name = "clickhouse-udfs",
    description = ["ClickHouse UDF for working with archived GTFS-rt data"],
    mixinStandardHelpOptions = true,
    usageHelpAutoWidth = true,
    versionProvider = VersionProvider::class,
    subcommands = [Reparse::class]
)
class CliParent

@Command(name = "reparse", description = ["Reparse GTFS-rt from/to protobuf, protobuf text format, or JSON"])
class Reparse : Callable<Int> {
    @Spec
    private lateinit var spec: CommandSpec

    @Parameters(index = "0", defaultValue = "-")
    private lateinit var input: File

    val inputStream: InputStream
        get() = if (input.name.equals("-")) {
            System.`in`
        } else {
            input.inputStream()
        }

    override fun call(): Int {
        val reader = om.readerFor(Input::class.java)
        val iter: MappingIterator<Input> = reader.readValues(inputStream)

        val writer = om.writerFor(Output::class.java)
        val sw = writer.withRootValueSeparator("\n").writeValues(spec.commandLine().out)

        iter.use {
            it.forEach { row ->
                val registry = ExtensionRegistry.newInstance()
                row.enabledExtensions?.forEach { extension -> extension.registerExtension(registry) }

                val fm = row.inputFormat.parseInput(row.data, registry)
                val outData = row.outputFormat.generateOutput(fm, registry)

                sw.write(Output(outData))
            }
        }

        return ExitCode.OK
    }
}

fun main(args: Array<String>): Unit = exitProcess(CommandLine(CliParent()).execute(*args))

@Suppress("unused")
internal enum class Format {
    PROTOBUF {
        override fun parseInput(
            input: String,
            registry: ExtensionRegistry
        ): FeedMessage = FeedMessage.parseFrom(Base64.decode(input), registry)

        override fun generateOutput(
            fm: FeedMessage,
            registry: ExtensionRegistry
        ): String = Base64.encode(fm.toByteArray())
    },
    PBTEXT {
        override fun parseInput(
            input: String,
            registry: ExtensionRegistry
        ): FeedMessage = TextFormat.parse(input, registry, FeedMessage::class.java)

        override fun generateOutput(
            fm: FeedMessage,
            registry: ExtensionRegistry
        ): String = fm.toString()
    },
    JSON {
        private fun mapper(registry: ExtensionRegistry): ObjectMapper = om.rebuild().addModule(
            ProtobufModule(
                ProtobufJacksonConfig.builder()
                    .extensionRegistry(registry)
                    .build()
            )
        ).build()

        override fun parseInput(
            input: String,
            registry: ExtensionRegistry
        ): FeedMessage = mapper(registry).readValue(input, FeedMessage::class.java)

        override fun generateOutput(
            fm: FeedMessage,
            registry: ExtensionRegistry
        ): String = mapper(registry).writeValueAsString(fm)
    };

    abstract fun parseInput(input: String, registry: ExtensionRegistry): FeedMessage
    abstract fun generateOutput(fm: FeedMessage, registry: ExtensionRegistry): String
}

@JsonNaming(SnakeCaseStrategy::class)
internal data class Input(
    val data: String,
    val enabledExtensions: Set<GtfsRealtimeExtension>?,
    val inputFormat: Format,
    val outputFormat: Format
)

@JsonNaming(SnakeCaseStrategy::class)
internal data class Output(
    val data: String
)