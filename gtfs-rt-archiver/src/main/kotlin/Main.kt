@file:OptIn(ExperimentalHoplite::class, ExperimentalTime::class)

package systems.choochoo.transit_data_archivers.gtfsrt

import com.sksamuel.hoplite.ConfigException
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addFileSource
import io.github.oshai.kotlinlogging.KotlinLogging
import io.prometheus.metrics.core.metrics.Info
import io.prometheus.metrics.exporter.httpserver.HTTPServer
import io.prometheus.metrics.instrumentation.guava.CacheMetricsCollector
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics
import io.prometheus.metrics.model.registry.PrometheusRegistry
import picocli.CommandLine
import picocli.CommandLine.*
import systems.choochoo.transit_data_archivers.common.utils.VersionProvider
import java.nio.file.Path
import kotlin.reflect.jvm.javaMethod
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant


val cacheMetrics = CacheMetricsCollector()

private val log = KotlinLogging.logger {}

@Command(
    name = "gtfs-rt-archiver",
    description = ["Archive GTFS-rt feeds to a ClickHouse database"],
    mixinStandardHelpOptions = true,
    usageHelpAutoWidth = true,
    versionProvider = VersionProvider::class,
)
internal fun runArchiver(
    @Option(names = ["--one-shot"], description = ["Enable one-shot mode: archive each feed once, then exit"])
    oneShot: Boolean = false,

    @Parameters(index = "0", description = ["Path to configuration file"], paramLabel = "<file>")
    configurationFile: Path
): Int {
    val configuration = try {
        ConfigLoaderBuilder.default()
            .addFileSource(configurationFile.toFile())
            .withExplicitSealedTypes()
            .strict()
            .build()
            .loadConfigOrThrow<Configuration>()
    } catch (ce: ConfigException) {
        log.error(ce) { "Could not load configuration file" }

        return ExitCode.USAGE
    }

    configuration.feeds
        .groupingBy { Pair(it.producer, it.feed) }
        .eachCount()
        .filterValues { it > 1 }
        .apply {
            if (isNotEmpty()) {
                forEach {
                    log.error {
                        "Feed ${it.key.first} ${it.key.second} is defined more than once; feed names must be unique"
                    }
                }
                return ExitCode.USAGE
            }
        }

    val af = DaggerArchiverFactory
        .builder()
        .configuration(configuration)
        .oneShot(oneShot)
        .build()

    val theArchiver = af.archiver()
    val appVersion = af.appVersion()
    val shutdownListener = af.schedulerShutdownListener()
    val errorListener = af.schedulerErrorListener()

    JvmMetrics.builder().register()

    PrometheusRegistry.defaultRegistry.register(cacheMetrics)

    Info.builder()
        .name("gtfs_rt_archiver_info")
        .help("gtfs-rt-archiver version info")
        .labelNames("group", "artifact", "version", "branch", "commit", "buildTimestamp")
        .register()
        .setLabelValues(
            appVersion.groupId,
            appVersion.artifactId,
            appVersion.version,
            appVersion.branch,
            appVersion.commitId,
            appVersion.buildTimestamp.toKotlinInstant().epochSeconds.toString()
        )

    val server = HTTPServer.builder()
        .port(9400)
        .buildAndStart()

    theArchiver.start()

    if (shutdownListener.schedulerStarted) {
        shutdownListener.schedulerShutdownLatch.await()
    }

    theArchiver.stop()
    server.stop()

    return if (errorListener.schedulerTerminatedWithError) {
        ExitCode.SOFTWARE
    } else {
        ExitCode.OK
    }
}

fun main(args: Array<String>): Unit = exitProcess(CommandLine(::runArchiver.javaMethod).execute(*args))