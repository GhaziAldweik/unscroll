package dev.ghazi.igfocus.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseTest {

    private val now = 1_794_000_000_000L

    @Test
    fun `not paused by default`() {
        assertFalse(Pause.isPaused(Pause.NOT_PAUSED, now))
    }

    @Test
    fun `paused before the end time and not after it`() {
        val until = Pause.until(now, minutes = 15)
        assertEquals(now + 15 * 60_000L, until)
        assertTrue(Pause.isPaused(until, now))
        assertTrue(Pause.isPaused(until, until - 1))
    }

    @Test
    fun `pause expires by itself at the end time`() {
        val until = Pause.until(now, minutes = 5)
        assertFalse(Pause.isPaused(until, until))
        assertFalse(Pause.isPaused(until, until + 1))
    }

    @Test
    fun `until resumed never expires`() {
        assertTrue(Pause.isPaused(Pause.UNTIL_RESUMED, now))
        assertTrue(Pause.isPaused(Pause.UNTIL_RESUMED, Long.MAX_VALUE - 1))
    }
}
