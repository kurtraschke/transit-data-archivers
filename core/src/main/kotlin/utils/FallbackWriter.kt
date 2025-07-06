@file:OptIn(ExperimentalTime::class)

package systems.choochoo.transit_data_archivers.core.utils

import com.github.luben.zstd.Zstd
import io.github.oshai.kotlinlogging.KotlinLogging
import systems.choochoo.transit_data_archivers.core.utils.CompressionMode.*
import java.net.URLEncoder
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.text.Charsets.UTF_8
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val log = KotlinLogging.logger {}

interface Compressor {
    val filenameExtension: String

    fun compress(input: ByteArray): ByteArray
}

interface FallbackWriter {
    fun write(partitionKeys: LinkedHashMap<String, String>, fetchTime: Instant, data: ByteArray)
}

@Suppress("unused")
enum class CompressionMode : Compressor {
    NONE {
        override val filenameExtension = ""

        override fun compress(input: ByteArray): ByteArray = input
    },
    ZSTANDARD {
        override val filenameExtension = ".zst"

        override fun compress(input: ByteArray): ByteArray = Zstd.compress(input)
    }
}

class DummyFallbackWriter : FallbackWriter {
    override fun write(
        partitionKeys: LinkedHashMap<String, String>,
        fetchTime: Instant,
        data: ByteArray
    ) {
     log.warn { "Discarding fetch at $fetchTime as fallback writer is not configured" }
    }
}

class LocalPathFallbackWriter(val basePath: Path, val compressionMode: Compressor = NONE) : FallbackWriter {
    override fun write(partitionKeys: LinkedHashMap<String, String>, fetchTime: Instant, data: ByteArray) {
        val partitionPath = createPartitionPath(basePath, partitionKeys)

        partitionPath.createDirectories()

        val filename = "${fetchTime.epochSeconds}.json${compressionMode.filenameExtension}"

        partitionPath.resolve(filename).writeBytes(compressionMode.compress(data))
    }
}

internal fun createPartitionPath(basePath: Path, partitionKeys: LinkedHashMap<String, String>): Path =
    partitionKeys.entries.fold(basePath) { p, (k, v) -> p.resolve("$k=${URLEncoder.encode(v, UTF_8)}") }