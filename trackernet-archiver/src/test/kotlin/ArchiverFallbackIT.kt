@file:Suppress("HttpUrlsUsage")

package systems.choochoo.transit_data_archivers.trackernet

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
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
import kotlin.reflect.jvm.javaMethod


@WireMockTest(proxyMode = true, httpsEnabled = false)
@ExtendWith(SystemStubsExtension::class)
class ArchiverFallbackIT {

    @SystemStub
    private val variables = EnvironmentVariables()

    @Test
    fun testArchiver(wmRuntimeInfo: WireMockRuntimeInfo, @TempDir tempDir: Path) {
        val wm = wmRuntimeInfo.wireMock

        wm.register(
            get(urlPathEqualTo("/TrackerNet/PredictionSummary/W"))
                .withHost(equalTo("api.tfl.gov.uk"))
                .withQueryParam("app_key", equalTo(TEST_API_KEY))
                .willReturn(
                    ok()
                        .withBodyFile("W.xml")
                )
        )

        wm.register(
            get(urlPathEqualTo("/TrackerNet/PredictionDetailed/W/BNK"))
                .withHost(equalTo("api.tfl.gov.uk"))
                .withQueryParam("app_key", equalTo(TEST_API_KEY))
                .willReturn(
                    ok()
                        .withBodyFile("BNK.xml")
                )
        )

        wm.register(
            get(urlPathEqualTo("/TrackerNet/PredictionDetailed/W/WLO"))
                .withHost(equalTo("api.tfl.gov.uk"))
                .withQueryParam("app_key", equalTo(TEST_API_KEY))
                .willReturn(
                    ok()
                        .withBodyFile("WLO.xml")
                )
        )

        val testConfigFile = tempDir.resolve("test-config.yaml")
        val fallbackPath = tempDir.resolve("fallback").createDirectory()

        testConfigFile.writeText(TEST_CONFIGURATION_YAML)

        //https://www.rfc-editor.org/rfc/rfc6761.html#section-6.4

        val clickhouseUrl = "http://clickhouse.invalid:8123/"

        variables.set("config.override.appKey", TEST_API_KEY)
        variables.set("config.override.database.url", clickhouseUrl)
        variables.set("config.override.fallback.basePath", fallbackPath.toAbsolutePath().toString())

        val cmd = CommandLine(::runArchiver.javaMethod)

        val exitCode = cmd.execute("--one-shot", testConfigFile.toAbsolutePath().toString())

        assertEquals(ExitCode.OK, exitCode)

        assertDoesNotThrow {
            verify(0, getRequestedFor(urlPathEqualTo("/TrackerNet/PredictionDetailed/W/WLO")))
        }

        assertEquals(
            2,
            Files.walk(fallbackPath)
                .filter { Files.isRegularFile(it) && it.extension == "json" }
                .count()
        )

    }

    companion object {
        const val TEST_API_KEY = "test-test-test"

        const val TEST_CONFIGURATION_YAML = """
baseUrl: http://api.tfl.gov.uk/TrackerNet/

database:
  options:
    database: "default"
    
fallback:
    enabled: true

lines:
  - line_code: "W"
"""
    }
}