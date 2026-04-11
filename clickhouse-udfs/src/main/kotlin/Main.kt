package systems.choochoo.transit_data_archivers.udf

import com.fasterxml.jackson.core.StreamReadFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.treeToValue
import com.google.protobuf.ExtensionRegistry
import com.google.transit.realtime.GtfsRealtime.FeedMessage
import com.hubspot.jackson.datatype.protobuf.ProtobufJacksonConfig
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import org.msgpack.core.MessagePack
import org.msgpack.value.impl.ImmutableBinaryValueImpl
import picocli.CommandLine
import picocli.CommandLine.*
import systems.choochoo.transit_data_archivers.common.utils.VersionProvider
import systems.choochoo.transit_data_archivers.gtfsrt.extensions.GtfsRealtimeExtension
import java.io.File
import java.io.InputStream
import java.util.concurrent.Callable
import kotlin.system.exitProcess


private val om = jsonMapper {
    addModule(kotlinModule())
    enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
    enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
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

@Command(name = "reparse", description = ["Reparse GTFS-rt from JSON to binary protobuf or pbtext"])
class Reparse : Callable<Int> {
    @Parameters(index = "0", defaultValue = "-")
    private lateinit var input: File

    val inputStream: InputStream
        get() = if (input.name.equals("-")) {
            System.`in`
        } else {
            input.inputStream()
        }

    override fun call(): Int {
        val up = MessagePack.newDefaultUnpacker(inputStream)
        val p = MessagePack.newDefaultPacker(System.out)

        while (up.hasNext()) {
            val a = up.unpackValue().asArrayValue()

            require(a.size() == 3)

            val responseContents = a.get(0).asBinaryValue().asString()
            val enabledExtensions = a.get(1).asArrayValue().list()
                .map {
                    om.treeToValue<GtfsRealtimeExtension>(
                        JsonNodeFactory.instance.textNode(
                            it.asBinaryValue().asString()
                        )
                    )
                }
            val outputFormat =
                om.treeToValue<Format>(JsonNodeFactory.instance.textNode(a.get(2).asBinaryValue().asString()))


            val registry = ExtensionRegistry.newInstance()
            enabledExtensions.forEach { it.registerExtension(registry) }

            val fm = om.rebuild()
                .addModule(
                    ProtobufModule(
                        ProtobufJacksonConfig.builder()
                            .extensionRegistry(registry)
                            .build()
                    )
                )
                .build()
                .readValue<FeedMessage>(responseContents)

            val outData = outputFormat.generateOutput(fm)

            p.packValue(ImmutableBinaryValueImpl(outData))
        }

        p.close()

        return ExitCode.OK
    }
}

fun main(args: Array<String>): Unit = exitProcess(CommandLine(CliParent()).execute(*args))

@Suppress("unused")
internal enum class Format {
    PROTOBUF {
        override fun generateOutput(fm: FeedMessage): ByteArray = fm.toByteArray()
    },
    PBTEXT {
        override fun generateOutput(fm: FeedMessage): ByteArray = fm.toString().encodeToByteArray()
    };

    abstract fun generateOutput(fm: FeedMessage): ByteArray
}