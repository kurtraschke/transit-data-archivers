package systems.choochoo.transit_data_archivers.common.utils

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Path

class FallbackWriterTest {
    @Test
    fun createPartitionPath() {
        val path = createPartitionPath(
            Path.of("/foo/bar"),
            linkedMapOf("producer" to "MTA NYCT Subway", "feed" to "ACE")
        )

        val expected = Path.of("/foo/bar/producer=MTA+NYCT+Subway/feed=ACE")

        Assertions.assertEquals(expected, path)
    }

}