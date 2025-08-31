@file:OptIn(ExperimentalTime::class)

package systems.choochoo.transit_data_archivers.njt

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


@Command(
    name = "njt-api-proxy",
    description = ["Proxy to provide a standard interface for New Jersey Transit GTFS and GTFS-rt APIs"],
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

    @Option(
        names = ["-D", "--data-dir"],
        description = ["Path to data directory where tokens will be cached"],
        paramLabel = "<path>",
        required = true,
        defaultValue = $$"${env:DATA_DIR}"
    )
    dataPath: Path,

    @Option(
        names = ["-U", "--api-username"],
        description = ["Username for New Jersey Transit API"],
        paramLabel = "<username>",
        required = true,
        defaultValue = $$"${env:API_USERNAME}"
    )
    username: String,

    @Option(
        names = ["-P", "--api-password"],
        description = ["Password for New Jersey Transit API"],
        paramLabel = "<password>",
        required = true,
        defaultValue = $$"${env:API_PASSWORD}"
    )
    password: String,

    @Option(
        names = ["-C", "--operator-contact"],
        description = ["Contact information for operator"],
        paramLabel = "<contact information>",
        required = false,
        defaultValue = $$"${env:OPERATOR_CONTACT}"
    )
    operatorContact: String?
): Int {
    val shutdownLatch = CountDownLatch(1)

    val pf = DaggerProxyFactory
        .builder()
        .host(host)
        .port(port)
        .dataPath(dataPath)
        .username(username)
        .password(password)
        .operatorContact(operatorContact)
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
        .name("njt_api_proxy_info")
        .help("njt-api-proxy version info")
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