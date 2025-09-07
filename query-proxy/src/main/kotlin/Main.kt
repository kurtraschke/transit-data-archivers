@file:OptIn(ExperimentalTime::class, ExperimentalHoplite::class)

package systems.choochoo.transit_data_archivers.query_proxy

import com.sksamuel.hoplite.ConfigException
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addFileSource
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.DiskSpaceMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.prometheus.metrics.core.metrics.Info
import picocli.CommandLine
import picocli.CommandLine.*
import systems.choochoo.transit_data_archivers.common.utils.VersionProvider
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import kotlin.reflect.jvm.javaMethod
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant

private val log = KotlinLogging.logger {}

@Command(
    name = "query-proxy",
    description = ["ClickHouse REST query proxy"],
    mixinStandardHelpOptions = true,
    usageHelpAutoWidth = true,
    versionProvider = VersionProvider::class
)
internal fun runProxy(
    @Option(
        names = ["-H", "--host"],
        description = [$$"Hostname or IP address to listen on; defaults to ${DEFAULT-VALUE}"],
        paramLabel = "<host>",
        required = false,
        defaultValue = $$"${env:HOST:-localhost}"
    )
    host: String,

    @Option(
        names = ["-p", "--port"],
        description = [$$"Port number to listen on; defaults to ${DEFAULT-VALUE}"],
        paramLabel = "<port>",
        required = false,
        defaultValue = $$"${env:PORT:-8000}"
    )
    port: Int,

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

    val shutdownLatch = CountDownLatch(1)

    val pf = DaggerQueryProxyFactory
        .builder()
        .host(host)
        .port(port)
        .configuration(configuration)
        .shutdownLatch(shutdownLatch)
        .build()

    val proxy = pf.proxy()

    val appVersion = pf.appVersion()

    val meterRegistry = pf.prometheusMeterRegistry()

    ClassLoaderMetrics().bindTo(meterRegistry)
    JvmMemoryMetrics().bindTo(meterRegistry)
    JvmGcMetrics().bindTo(meterRegistry)
    JvmThreadMetrics().bindTo(meterRegistry)
    UptimeMetrics().bindTo(meterRegistry)
    ProcessorMetrics().bindTo(meterRegistry)
    DiskSpaceMetrics(File(System.getProperty("user.dir"))).bindTo(meterRegistry)

    Info.builder()
        .name("query_proxy_info")
        .help("query-proxy version info")
        .labelNames("group", "artifact", "version", "branch", "commit", "buildTimestamp")
        .register(meterRegistry.prometheusRegistry)
        .setLabelValues(
            appVersion.groupId,
            appVersion.artifactId,
            appVersion.version,
            appVersion.branch,
            appVersion.commitId,
            appVersion.buildTimestamp.toKotlinInstant().epochSeconds.toString()
        )

    Runtime.getRuntime().addShutdownHook(Thread { proxy.stop() })

    proxy.start()

    shutdownLatch.await()

    return ExitCode.OK
}


fun main(args: Array<String>): Unit = exitProcess(CommandLine(::runProxy.javaMethod).execute(*args))