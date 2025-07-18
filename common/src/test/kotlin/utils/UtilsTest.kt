package systems.choochoo.transit_data_archivers.common.utils

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@ExtendWith(MockKExtension::class)
class UtilsTest {

    @MockK
    lateinit var random: Random

    @Test
    fun randomDuration() {
        every { random.nextDouble(0.0, 0.5) } returns 0.25

        val actual = randomDuration(1.minutes, random)

        val expected = 15.seconds

        assertEquals(expected, actual)
    }
}