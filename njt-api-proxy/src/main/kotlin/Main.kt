package systems.choochoo.transit_data_archivers.njt

import picocli.CommandLine
import picocli.CommandLine.*
import systems.choochoo.transit_data_archivers.common.utils.VersionProvider
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import kotlin.reflect.jvm.javaMethod
import kotlin.system.exitProcess

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

    Runtime.getRuntime().addShutdownHook(Thread { proxy.stop() })

    proxy.start()

    shutdownLatch.await()

    return ExitCode.OK
}


fun main(args: Array<String>): Unit = exitProcess(CommandLine(::runProxy.javaMethod).execute(*args))