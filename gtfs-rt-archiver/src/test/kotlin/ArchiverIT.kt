package systems.choochoo.transit_data_archivers.gtfsrt

import com.clickhouse.client.api.Client
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import org.junit.jupiter.api.Assertions.*
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

@Testcontainers
@WireMockTest(proxyMode = true)
@ExtendWith(SystemStubsExtension::class)
class ArchiverIT {

    @SystemStub
    private val variables = EnvironmentVariables()

    @Test
    fun testFetcher(@TempDir tempDir: Path) {
        val testConfigFile = tempDir.resolve("testConfiguration.yaml")

        testConfigFile.writeText(TEST_CONFIGURATION_YAML)

        val clickhouseUrl = "http://${chContainer.host}:${chContainer.getMappedPort(8123)}/"

        variables.set("config.override.database.url", clickhouseUrl)
        variables.set("config.override.database.username", chContainer.username)
        variables.set("config.override.database.password", chContainer.password)

        val cmd = CommandLine(ArchiverCli::class.java)

        val exitCode = cmd.execute("--one-shot", testConfigFile.toAbsolutePath().toString())

        assertEquals(ExitCode.OK, exitCode)

        val client = Client.Builder()
            .addEndpoint(clickhouseUrl)
            .setUsername(chContainer.username)
            .setPassword(chContainer.password)
            .build()

        client.use {
            assertEquals(4, getRowCount(it, "feed_contents"))
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
CREATE TABLE feed_contents
(
    producer LowCardinality(String),
    feed LowCardinality(String),
    fetch_time DateTime('UTC') CODEC(DoubleDelta, LZ4),
    is_error Bool,
    error_message Nullable(String) CODEC(ZSTD),
    response_time_millis Nullable(UInt32) CODEC(Delta, LZ4),
    status_code Nullable(UInt16),
    status_message LowCardinality(Nullable(String)),
    protocol LowCardinality(Nullable(String)),
    response_headers Nullable(JSON) CODEC(ZSTD(3)),
    response_body Nullable(String) CODEC(ZSTD(3)),
    response_body_length Nullable(UInt32) CODEC(Delta, LZ4),
    response_contents Nullable(JSON) CODEC(ZSTD(3)),
    enabled_extensions Array(LowCardinality(String))
)
ENGINE = MergeTree
ORDER BY (producer, feed, fetch_time);

-- This rigamarole is due to https://github.com/ClickHouse/ClickHouse/issues/46837, and in turn due to the fact that
-- at the moment there does not appear to be a good way to insert a byte array into a ClickHouse string
-- from Java using the RowBinary format.

CREATE TABLE feed_contents_archiver_input
(
    producer String,
    feed String,
    fetch_time DateTime('UTC'),
    is_error Bool,
    error_message Nullable(String),
    response_time_millis Nullable(UInt32),
    status_code Nullable(UInt16),
    status_message Nullable(String),
    protocol Nullable(String),
    response_headers Nullable(JSON),
    response_body_b64 Nullable(String),
    response_body_length Nullable(UInt32),
    response_contents Nullable(JSON),
    enabled_extensions Array(String)
)
ENGINE = Null;

CREATE MATERIALIZED VIEW mv_feed_contents_archiver_input TO feed_contents AS
SELECT producer, feed, fetch_time, is_error, error_message, response_time_millis,
status_code, status_message, protocol, response_headers,
base64Decode(response_body_b64) response_body, response_body_length,
response_contents, enabled_extensions
FROM feed_contents_archiver_input;
"""

const val TEST_CONFIGURATION_YAML = """
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
"""
