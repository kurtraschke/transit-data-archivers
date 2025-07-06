package systems.choochoo.transit_data_archivers.gtfsrt

import com.github.tomakehurst.wiremock.junit5.WireMockTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import picocli.CommandLine
import picocli.CommandLine.ExitCode
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStub
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.extension
import kotlin.io.path.writeText

@WireMockTest(proxyMode = true)
@ExtendWith(SystemStubsExtension::class)
class ArchiverFallbackIT {

    @SystemStub
    private val variables = EnvironmentVariables()

    @Test
    fun testFetcher(@TempDir tempDir: Path) {
        val testConfigFile = tempDir.resolve("testConfiguration.yaml")

        val fallbackPath = tempDir.resolve("fallback").createDirectory()

        testConfigFile.writeText(TEST_CONFIGURATION_YAML)

        //https://www.rfc-editor.org/rfc/rfc6761.html#section-6.4

        val clickhouseUrl = "http://clickhouse.invalid:8123/"

        variables.set("config.override.database.url", clickhouseUrl)
        variables.set("config.override.fallback.basePath", fallbackPath.toAbsolutePath().toString())

        val cmd = CommandLine(ArchiverCli::class.java)

        val exitCode = cmd.execute("--one-shot", testConfigFile.toAbsolutePath().toString())

        assertEquals(ExitCode.OK, exitCode)

        assertEquals(
            4,
            Files.walk(fallbackPath)
                .filter { Files.isRegularFile(it) && it.extension == "json" }
                .count()
        )

    }

    companion object {
        private val TEST_CONFIGURATION_YAML = """
            fallback:
                enabled: true
            
            feeds:
              - producer: MBTA
                feed: TU
                feedUrl: https://cdn.mbta.com/realtime/TripUpdates.pb
                ignoreTLSErrors: true
            
              - producer: MBTA
                feed: VP
                feedUrl: https://cdn.mbta.com/realtime/VehiclePositions.pb
                ignoreTLSErrors: true
            
              - producer: MBTA
                feed: Alerts
                feedUrl: https://cdn.mbta.com/realtime/Alerts.pb
                ignoreTLSErrors: true
            
              - producer: Test
                feed: Does Not Exist
                feedUrl: http://streetcarnameddesire.test/tripUpdates.pb
                """.trimIndent()

    }

}



