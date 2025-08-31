@file:OptIn(ExperimentalTime::class)

package systems.choochoo.transit_data_archivers.common.utils

import com.github.luben.zstd.Zstd
import io.github.oshai.kotlinlogging.KotlinLogging
import systems.choochoo.transit_data_archivers.common.utils.CompressionMode.*
import java.net.URLEncoder
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory
import kotlin.io.path.isReadable
import kotlin.io.path.isWritable
import kotlin.io.path.writeBytes
import kotlin.text.Charsets.UTF_8
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val log = KotlinLogging.logger {}

interface FallbackWriter {
    fun write(partitionKeys: LinkedHashMap<String, String>, fetchTime: Instant, data: ByteArray)
}

@Suppress("unused")
enum class CompressionMode(val filenameExtension: String = "") {
    NONE {
        override fun compress(input: ByteArray): ByteArray = input
    },
    ZSTANDARD(".zst") {
        override fun compress(input: ByteArray): ByteArray = Zstd.compress(input)

        override fun isAvailable() =
            try {
                Class.forName("com.github.luben.zstd.Zstd")
                true
            } catch (e: ClassNotFoundException) {
                false
            }
    };

    abstract fun compress(input: ByteArray): ByteArray

    open fun isAvailable(): Boolean = true
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

class LocalPathFallbackWriter(val basePath: Path, val compressionMode: CompressionMode = NONE) : FallbackWriter {
    init {
        require(basePath.isDirectory() && basePath.isReadable() && basePath.isWritable()) { "$basePath must be a directory that is readable and writable" }
        require(compressionMode.isAvailable()) { "Compression mode $compressionMode is not available" }
    }

    override fun write(partitionKeys: LinkedHashMap<String, String>, fetchTime: Instant, data: ByteArray) {
        val partitionPath = createPartitionPath(basePath, partitionKeys)

        partitionPath.createDirectories()

        val filename = "${fetchTime.epochSeconds}.json${compressionMode.filenameExtension}"

        partitionPath.resolve(filename).writeBytes(compressionMode.compress(data))
    }
}

internal fun createPartitionPath(basePath: Path, partitionKeys: LinkedHashMap<String, String>): Path =
    partitionKeys.entries.fold(basePath) { p, (k, v) -> p.resolve("$k=${URLEncoder.encode(v, UTF_8)}") }