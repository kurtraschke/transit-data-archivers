@file:Suppress("HttpUrlsUsage")

package systems.choochoo.transit_data_archivers.trackernet

import com.clickhouse.client.api.Client
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.clickhouse.ClickHouseContainer
import org.testcontainers.images.builder.Transferable
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import picocli.CommandLine
import picocli.CommandLine.ExitCode
import systems.choochoo.transit_data_archivers.core.utils.clickhouseImageName
import systems.choochoo.transit_data_archivers.core.utils.getRowCount
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStub
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension
import java.nio.file.Path
import kotlin.io.path.writeText


const val TEST_API_KEY = "test-test-test"

@Testcontainers
@WireMockTest(proxyMode = true, httpsEnabled = false)
@ExtendWith(SystemStubsExtension::class)
class ArchiverIT {

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

        testConfigFile.writeText(TEST_CONFIGURATION_YAML)

        val clickhouseUrl = "http://${chContainer.host}:${chContainer.getMappedPort(8123)}/"

        variables.set("config.override.appKey", TEST_API_KEY)
        variables.set("config.override.database.url", clickhouseUrl)
        variables.set("config.override.database.username", chContainer.username)
        variables.set("config.override.database.password", chContainer.password)

        val cmd = CommandLine(ArchiverCli::class.java)

        val exitCode = cmd.execute("--one-shot", testConfigFile.toAbsolutePath().toString())

        assertEquals(ExitCode.OK, exitCode)

        assertDoesNotThrow {
            verify(0, getRequestedFor(urlPathEqualTo("/TrackerNet/PredictionDetailed/W/WLO")))
        }

        val client = Client.Builder()
            .addEndpoint(clickhouseUrl)
            .setUsername(chContainer.username)
            .setPassword(chContainer.password)
            .build()

        client.use {
            assertEquals(1, getRowCount(it, "prediction_summary"))
            // Two rows, not one, because there were no trains at WLO, so we should not have fetched it!
            // This is also validated by the WireMock assertion above.
            assertEquals(1, getRowCount(it, "prediction_details"))
        }

    }

    companion object {
        @Container
        @JvmField
        var chContainer: ClickHouseContainer = ClickHouseContainer(
            DockerImageName
                .parse(clickhouseImageName)
                .asCompatibleSubstituteFor("clickhouse/clickhouse-server")
            )
            .withCopyToContainer(
                Transferable.of(TEST_SETUP_SQL),
                "/docker-entrypoint-initdb.d/test-setup.sql"
            )
    }
}

const val TEST_SETUP_SQL = """
CREATE TABLE prediction_summary
(
    fetch_time DateTime('UTC') CODEC(DoubleDelta),
    line_code LowCardinality(String),
    prediction_summary_json JSON CODEC(ZSTD(3))
)
ENGINE = MergeTree
PRIMARY KEY (fetch_time, line_code)
ORDER BY (fetch_time, line_code);

CREATE TABLE prediction_details
(
    fetch_time DateTime('UTC') CODEC(DoubleDelta),
    line_code LowCardinality(String),
    station_code LowCardinality(String),
    prediction_details_json JSON CODEC(ZSTD(3))
)
ENGINE = MergeTree
PRIMARY KEY (fetch_time, line_code, station_code)
ORDER BY (fetch_time, line_code, station_code);
"""

const val TEST_CONFIGURATION_YAML = """
baseUrl: http://api.tfl.gov.uk/TrackerNet/

database:
  options:
    database: "default"

lines:
  - line_code: "W"
"""