@file:OptIn(ExperimentalHoplite::class)

package systems.choochoo.transit_data_archivers.trackernet

import com.sksamuel.hoplite.ConfigException
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addFileSource
import io.github.oshai.kotlinlogging.KotlinLogging
import picocli.CommandLine
import picocli.CommandLine.*
import systems.choochoo.transit_data_archivers.common.utils.VersionProvider
import java.nio.file.Path
import kotlin.reflect.jvm.javaMethod
import kotlin.system.exitProcess

private val log = KotlinLogging.logger {}

@Command(
    name = "trackernet-archiver",
    description = ["Archive London Underground Trackernet feeds to a ClickHouse database"],
    mixinStandardHelpOptions = true,
    usageHelpAutoWidth = true,
    versionProvider = VersionProvider::class,
)
internal fun runArchiver(
    @Option(names = ["--one-shot"], description = ["Enable one-shot mode: archive each line once, then exit"])
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

    val af = DaggerArchiverFactory
        .builder()
        .configuration(configuration)
        .oneShot(oneShot)
        .build()

    val theArchiver = af.archiver()
    val shutdownListener = af.schedulerShutdownListener()
    val errorListener = af.schedulerErrorListener()

    theArchiver.start()

    if (shutdownListener.schedulerStarted) {
        shutdownListener.schedulerShutdownLatch.await()
    }

    theArchiver.stop()

    return if (errorListener.schedulerTerminatedWithError) {
        ExitCode.SOFTWARE
    } else {
        ExitCode.OK
    }
}


fun main(args: Array<String>): Unit = exitProcess(CommandLine(::runArchiver.javaMethod).execute(*args))