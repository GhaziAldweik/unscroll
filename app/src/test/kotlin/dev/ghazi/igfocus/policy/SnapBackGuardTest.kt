package dev.ghazi.igfocus.policy

import dev.ghazi.igfocus.policy.SnapBackGuard.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapBackGuardTest {

    private val t0 = 1_794_000_000_000L

    @Test
    fun `snaps that bring Home back to rest can go on as long as the user keeps dragging`() {
        val guard = SnapBackGuard()
        var now = t0
        repeat(5) {
            assertEquals(Verdict.SNAP, guard.check(now))
            guard.onAtRest()
            now += 15_000
        }
        assertEquals(0, guard.failures)
    }

    @Test
    fun `snaps are spaced out`() {
        val guard = SnapBackGuard()
        assertEquals(Verdict.SNAP, guard.check(t0))
        assertEquals(Verdict.WAIT, guard.check(t0 + 1_000))
    }

    /** A wrong idea of "at rest" must not scroll Instagram for ever. */
    @Test
    fun `snaps that never reach rest stop after three`() {
        val guard = SnapBackGuard()
        assertEquals(Verdict.SNAP, guard.check(t0))
        assertEquals(0, guard.failures)
        assertEquals(Verdict.SNAP, guard.check(t0 + 2_000))
        assertEquals(1, guard.failures)
        assertEquals(Verdict.SNAP, guard.check(t0 + 4_000))
        assertEquals(2, guard.failures)
        assertEquals(Verdict.JUST_STOPPED, guard.check(t0 + 6_000))
        assertEquals(Verdict.STOPPED, guard.check(t0 + 8_000))
        assertTrue(guard.isStopped(t0 + 8_000))
    }

    @Test
    fun `too many snaps in a minute stop snapping even if each one works`() {
        val guard = SnapBackGuard(maxPerWindow = 3)
        var now = t0
        repeat(3) {
            assertEquals(Verdict.SNAP, guard.check(now))
            guard.onAtRest()
            now += 2_000
        }
        assertEquals(Verdict.JUST_STOPPED, guard.check(now))
    }

    @Test
    fun `reset clears a stop`() {
        val guard = SnapBackGuard(maxFailures = 1)
        guard.check(t0)
        assertEquals(Verdict.JUST_STOPPED, guard.check(t0 + 2_000))
        guard.reset()
        assertFalse(guard.isStopped(t0 + 2_000))
    }
}
