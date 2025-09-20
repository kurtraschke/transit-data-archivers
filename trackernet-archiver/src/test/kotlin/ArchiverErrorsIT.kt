package systems.choochoo.transit_data_archivers.trackernet

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import picocli.CommandLine
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText
import kotlin.reflect.jvm.javaMethod

class ArchiverErrorsIT {
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    fun testArchiverFailsCleanly(@TempDir tempDir: Path) {
        val testConfigFile = tempDir.resolve("test-config.yaml")

        testConfigFile.writeText(TEST_BAD_CONFIGURATION_YAML)

        val cmd = CommandLine(::runArchiver.javaMethod)

        val exitCode = cmd.execute("--one-shot", testConfigFile.toAbsolutePath().toString())

        assertEquals(CommandLine.ExitCode.SOFTWARE, exitCode)
    }

    companion object {
        @Suppress("HttpUrlsUsage")
        const val TEST_BAD_CONFIGURATION_YAML = """
baseUrl: http://api.tfl.gov.uk/TrackerNet # Retrofit will throw an error here because the base URL does not end in a trailing slash

appKey: 'foo'

database:
  url: 'https://foo'
  options:
    database: "default"

lines:
  - line_code: "W"
"""
    }
}