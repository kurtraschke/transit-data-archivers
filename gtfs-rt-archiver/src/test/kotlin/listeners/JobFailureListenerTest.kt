@file:OptIn(ExperimentalTime::class)

package systems.choochoo.transit_data_archivers.gtfsrt.listeners

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime


@ExtendWith(MockKExtension::class)
class JobFailureListenerTest {

    @MockK
    lateinit var random: Random

    @Test
    fun quantizeDuration() {
        val actual = quantizeDuration(189.seconds, 10.seconds)

        val expected = 190.seconds

        assertEquals(expected, actual)
    }

    @Test
    fun jitter() {
        every { random.nextDouble(0.8, 1.0) } returns 0.9

        val actual = jitter(30.seconds, 0.2, random)

        val expected = 27.seconds

        assertEquals(expected, actual)
    }
}