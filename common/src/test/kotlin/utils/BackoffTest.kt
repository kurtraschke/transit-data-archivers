@file:OptIn(ExperimentalTime::class)

package systems.choochoo.transit_data_archivers.common.utils

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@ExtendWith(MockKExtension::class)
class BackoffTest {

    @MockK
    lateinit var random: Random

    @Suppress("AssertBetweenInconvertibleTypes")
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

        val t0 = Instant.parse("2025-07-17T00:00:00Z")
        val p0 = null
        Assertions.assertEquals(p0, b.observeExecution(t0, false)) // no error -> no pause

        val t1 = t0 + 30.seconds
        val p1 = null
        Assertions.assertEquals(p1, b.observeExecution(t1, true)) // first failure -> no pause

        val t2 = t1 + 30.seconds
        val p2 = null
        Assertions.assertEquals(p2, b.observeExecution(t2, true)) // second failure -> no pause

        val t3 = t2 + 30.seconds
        val p3 = null
        Assertions.assertEquals(p3, b.observeExecution(t3, true)) // third failure -> no pause

        val t4 = t3 + 30.seconds
        val p4 = 60.seconds
        Assertions.assertEquals(p4, b.observeExecution(t4, true)) // fourth failure -> first pause

        val t5 = t4 + 60.seconds
        val p5 = 120.seconds
        Assertions.assertEquals(p5, b.observeExecution(t5, true)) // fifth failure -> second pause

        val t6 = t5 + 120.seconds
        val p6 = 240.seconds
        Assertions.assertEquals(p6, b.observeExecution(t6, true)) // sixth failure -> third pause

        val t7 = t6 + 240.seconds
        val p7 = 300.seconds
        Assertions.assertEquals(
            p7,
            b.observeExecution(t7, true)
        ) // seventh failure -> fourth pause (capped at 300 seconds)

        val t8 = t7 + 45.minutes
        val p8 = null
        Assertions.assertEquals(
            p8,
            b.observeExecution(t8, false)
        ) // reset period elapses without failure -> next error starts the cycle over again

        val t9 = t8 + 30.seconds
        val p9 = null
        Assertions.assertEquals(p9, b.observeExecution(t9, true)) // first failure -> no pause

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