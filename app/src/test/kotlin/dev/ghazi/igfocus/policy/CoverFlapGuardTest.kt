package dev.ghazi.igfocus.policy

import dev.ghazi.igfocus.service.Screen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverFlapGuardTest {

    private val t0 = 1_794_000_000_000L

    /** What happened on 14 Nov: Explore → reel → Back → Explore, ten times in twelve seconds. */
    @Test
    fun `bouncing between screens never stops a cover, however fast`() {
        val guard = CoverFlapGuard()
        var now = t0
        repeat(20) {
            guard.onScreen(Screen.SEARCH_EXPLORE)
            assertFalse(guard.onCoverAdded(Screen.SEARCH_EXPLORE, now))
            guard.onScreen(Screen.REEL_VIEWER)
            now += 300
        }
        assertFalse(guard.isStopped(now))
    }

    @Test
    fun `a cover that keeps reappearing on the same screen stops for a minute`() {
        val guard = CoverFlapGuard()
        guard.onScreen(Screen.HOME)
        assertFalse(guard.onCoverAdded(Screen.HOME, t0))
        repeat(4) {
            guard.onScreen(Screen.UNKNOWN)
            guard.onScreen(Screen.HOME)
            assertFalse(guard.onCoverAdded(Screen.HOME, t0 + (it + 1) * 500L))
        }
        guard.onScreen(Screen.UNKNOWN)
        assertTrue(guard.onCoverAdded(Screen.HOME, t0 + 3_000))
        assertTrue(guard.isStopped(t0 + 62_000))
        assertFalse(guard.isStopped(t0 + 63_000))
    }

    @Test
    fun `reset clears a stop`() {
        val guard = CoverFlapGuard(maxFlaps = 1)
        guard.onCoverAdded(Screen.HOME, t0)
        assertTrue(guard.onCoverAdded(Screen.HOME, t0 + 100))
        guard.reset()
        assertFalse(guard.isStopped(t0 + 100))
    }
}
