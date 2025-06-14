package systems.choochoo.transit_data_archivers.udf

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.databind.MappingIterator
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.fasterxml.jackson.databind.json.JsonMapper
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
import systems.choochoo.transit_data_archivers.gtfsrt.extensions.GtfsRealtimeExtension
import java.io.File
import java.io.InputStream
import java.util.concurrent.Callable
import kotlin.system.exitProcess


private val om = jsonMapper {
    addModule(kotlinModule())
}

@Command(name = "clickhouse-udfs", subcommands = [Reparse::class])
class CliParent

@Command(name = "reparse")
class Reparse : Callable<Int> {
    @Spec
    lateinit var spec: CommandSpec

    @Parameters(index = "0", defaultValue = "-")
    lateinit var input: File

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
                (row as InputCore).enabledExtensions.forEach { extension -> extension.registerExtension(registry) }

                val pbMapper: JsonMapper by lazy {
                    val config = ProtobufJacksonConfig.builder()
                        .extensionRegistry(registry)
                        .build()

                    om.rebuild().addModule(ProtobufModule(config)).build()
                }

                val fm = when (row) {
                    is Input.Json -> {
                        pbMapper.readValue(row.data, FeedMessage::class.java)
                    }

                    is Input.PbText -> {
                        TextFormat.parse(row.data, registry, FeedMessage::class.java)
                    }

                    is Input.Protobuf -> {
                        FeedMessage.parseFrom(row.data, registry)
                    }
                }

                val out = when (row.outputFormat) {
                    Format.PROTOBUF -> Output.Protobuf(fm.toByteArray())
                    Format.PBTEXT -> Output.PbText(fm.toString())
                    Format.JSON -> Output.Json(pbMapper.writeValueAsString(fm))
                }

                sw.write(out)
            }
        }

        return ExitCode.OK
    }
}

fun main(args: Array<String>): Unit = exitProcess(CommandLine(CliParent()).execute(*args))

internal interface InputCore {
    val enabledExtensions: Set<GtfsRealtimeExtension>
    val outputFormat: Format
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "input_format")
internal sealed class Input {
    @JsonNaming(SnakeCaseStrategy::class)
    @JsonTypeName("JSON")
    data class Json(
        val data: String,
        override val enabledExtensions: Set<GtfsRealtimeExtension> = emptySet(),
        override val outputFormat: Format
    ) : Input(), InputCore

    @JsonNaming(SnakeCaseStrategy::class)
    @JsonTypeName("PBTEXT")
    data class PbText(
        val data: String,
        override val enabledExtensions: Set<GtfsRealtimeExtension> = emptySet(),
        override val outputFormat: Format
    ) : Input(), InputCore

    @JsonNaming(SnakeCaseStrategy::class)
    @JsonTypeName("PROTOBUF")
    data class Protobuf(
        @Suppress("ArrayInDataClass") val data: ByteArray,
        override val enabledExtensions: Set<GtfsRealtimeExtension> = emptySet(),
        override val outputFormat: Format
    ) : Input(), InputCore
}

internal enum class Format {
    PROTOBUF,
    PBTEXT,
    JSON
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
internal sealed class Output {
    @JsonNaming(SnakeCaseStrategy::class)
    data class Json(
        val data: String
    ) : Output()

    @JsonNaming(SnakeCaseStrategy::class)
    data class PbText(
        val data: String
    ) : Output()


    @JsonNaming(SnakeCaseStrategy::class)
    data class Protobuf(
        @Suppress("ArrayInDataClass") val data: ByteArray
    ) : Output()
}