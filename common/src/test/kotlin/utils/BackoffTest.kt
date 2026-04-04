package systems.choochoo.transit_data_archivers.common.utils

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@ExtendWith(MockKExtension::class)
class BackoffTest {

    private data class BackoffTestEvent(
        val timeDelta: Duration,
        val isError: Boolean,
        val expectedBackoff: Duration?
    )

    @MockK
    lateinit var random: Random

    @Test
    fun `test backoff`() {
        val b = Backoff(
            3,
            30.seconds,
            2.0,
            5.minutes,
            30.minutes,
            0.0,
            15.seconds
        )

        var eventTime = Instant.parse("2025-07-17T00:00:00Z")

        listOf(
            BackoffTestEvent(30.seconds, false, null),
            BackoffTestEvent(30.seconds, true, null),
            BackoffTestEvent(30.seconds, true, null),
            BackoffTestEvent(30.seconds, true, null),
            BackoffTestEvent(30.seconds, true, 60.seconds),
            BackoffTestEvent(60.seconds, true, 120.seconds),
            BackoffTestEvent(120.seconds, true, 240.seconds),
            BackoffTestEvent(240.seconds, true, 300.seconds),
            BackoffTestEvent(300.seconds, true, 300.seconds),
            BackoffTestEvent(300.seconds, false, null),
            BackoffTestEvent(25.minutes, true, null),
            BackoffTestEvent(30.seconds, true, null),
        ).forEachIndexed { index, event ->
            eventTime += event.timeDelta
            Assertions.assertEquals(
                event.expectedBackoff,
                b.observeExecution(eventTime, event.isError),
                "at event $index, $event"
            )
        }
    }

    @Test
    fun `test backoff with intermittent failure`() {
        val b = Backoff(
            3,
            30.seconds,
            2.0,
            60.minutes,
            30.minutes,
            0.0,
            15.seconds
        )

        var eventTime = Instant.parse("2025-07-17T00:00:00Z")

        listOf(
            BackoffTestEvent(30.seconds, true, null),
            BackoffTestEvent(30.seconds, false, null),
            BackoffTestEvent(30.seconds, true, null),
            BackoffTestEvent(30.seconds, true, null),
            BackoffTestEvent(30.seconds, true, null),
            BackoffTestEvent(30.seconds, false, null),
            BackoffTestEvent(30.seconds, true, null),
            BackoffTestEvent(30.seconds, true, null),
            BackoffTestEvent(30.seconds, true, null),
            BackoffTestEvent(30.seconds, true, 60.seconds),
            BackoffTestEvent(60.seconds, true, 120.seconds),
            BackoffTestEvent(120.seconds, false, null),
            BackoffTestEvent(30.seconds, true, null),
            BackoffTestEvent(26.minutes, false, null),
            BackoffTestEvent(30.seconds, true, null),
            BackoffTestEvent(30.seconds, true, null),
            BackoffTestEvent(30.seconds, true, null),
            BackoffTestEvent(30.seconds, true, 60.seconds),
        ).forEachIndexed { index, event ->
            eventTime += event.timeDelta
            Assertions.assertEquals(
                event.expectedBackoff,
                b.observeExecution(eventTime, event.isError),
                "at event $index, $event"
            )
        }
    }


    @Test
    fun quantizeDuration() {
        val actual = Backoff.quantizeDuration(189.seconds, 10.seconds)

        val expected = 190.seconds

        Assertions.assertEquals(expected, actual)
    }

    @Test
    fun jitter() {
        every { random.nextDouble(0.8, 1.2) } returns 0.9

        val actual = Backoff.jitter(30.seconds, 0.2, random)

        val expected = 27.seconds

        Assertions.assertEquals(expected, actual)
    }
}