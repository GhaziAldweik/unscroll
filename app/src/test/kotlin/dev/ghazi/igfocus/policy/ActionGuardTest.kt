package dev.ghazi.igfocus.policy

import dev.ghazi.igfocus.policy.ActionGuard.Verdict
import dev.ghazi.igfocus.service.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionGuardTest {

    private val t0 = 1_794_000_000_000L

    @Test
    fun `scans right after an action wait instead of repeating it`() {
        val guard = ActionGuard()
        assertEquals(Verdict.ACT, guard.check(Screen.REELS_TAB, t0))
        assertEquals(Verdict.WAIT, guard.check(Screen.REELS_TAB, t0 + 300))
        assertEquals(Verdict.WAIT, guard.check(Screen.REELS_TAB, t0 + 999))
    }

    @Test
    fun `an action that works never stops blocking`() {
        val guard = ActionGuard()
        var now = t0
        repeat(5) {
            assertEquals(Verdict.ACT, guard.check(Screen.REELS_TAB, now))
            guard.onScreen(Screen.DM_INBOX)
            now += 20_000
        }
        assertFalse(guard.isStopped(now))
    }

    /** The runaway case: the screen never changes, so the action is not working. */
    @Test
    fun `an action that does nothing stops after three attempts`() {
        val guard = ActionGuard()
        assertEquals(Verdict.ACT, guard.check(Screen.REELS_TAB, t0))
        assertEquals(1, guard.attempts)
        guard.onScreen(Screen.REELS_TAB)
        assertEquals(Verdict.ACT, guard.check(Screen.REELS_TAB, t0 + 1_000))
        assertEquals(2, guard.attempts)
        guard.onScreen(Screen.UNKNOWN)
        assertEquals(Verdict.ACT, guard.check(Screen.REELS_TAB, t0 + 2_000))
        assertEquals(3, guard.attempts)
        assertEquals(Verdict.JUST_STOPPED, guard.check(Screen.REELS_TAB, t0 + 3_000))
        assertEquals(Verdict.STOPPED, guard.check(Screen.REELS_TAB, t0 + 4_000))
        assertEquals(Screen.REELS_TAB, guard.stoppedOn)
        assertTrue(guard.isStopped(t0 + 4_000))
    }

    /** Testing by hand: swiping into Reels again and again must keep working. */
    @Test
    fun `a dozen deliberate visits in a minute keep being blocked`() {
        val guard = ActionGuard()
        var now = t0
        repeat(12) {
            assertEquals(Verdict.ACT, guard.check(Screen.REELS_TAB, now))
            guard.onScreen(Screen.DM_INBOX)
            now += 4_000
        }
    }

    /** Back on a reel can land on another reel; giving up would hand over ten minutes of reels. */
    @Test
    fun `Back on a reel that keeps landing on reels slows down but never stops`() {
        val guard = ActionGuard(throttledIntervalMs = 2_000)
        assertEquals(Verdict.ACT, guard.check(Screen.REEL_VIEWER, t0, neverStop = true))
        assertEquals(Verdict.ACT, guard.check(Screen.REEL_VIEWER, t0 + 1_000, neverStop = true))
        assertEquals(Verdict.ACT, guard.check(Screen.REEL_VIEWER, t0 + 2_000, neverStop = true))
        assertEquals(Verdict.WAIT, guard.check(Screen.REEL_VIEWER, t0 + 3_000, neverStop = true))
        assertEquals(Verdict.ACT, guard.check(Screen.REEL_VIEWER, t0 + 4_000, neverStop = true))
        assertEquals(Verdict.ACT, guard.check(Screen.REEL_VIEWER, t0 + 6_000, neverStop = true))
        assertFalse(guard.isStopped(t0 + 6_000))
    }

    /** Stopping here would be a way around blocking: hammer reels until it gives up. */
    @Test
    fun `too many actions in a minute slow blocking down but never stop it`() {
        val guard = ActionGuard(maxActionsPerWindow = 4, throttledIntervalMs = 2_000)
        var now = t0
        repeat(4) {
            assertEquals(Verdict.ACT, guard.check(Screen.REEL_VIEWER, now))
            guard.onScreen(Screen.SEARCH_EXPLORE)
            now += 1_200
        }
        assertEquals(Verdict.WAIT, guard.check(Screen.REEL_VIEWER, now))
        assertEquals(Verdict.ACT, guard.check(Screen.REEL_VIEWER, now + 900))
        assertFalse(guard.isStopped(now + 900))
    }

    @Test
    fun `a stop wears off after its time and on reset`() {
        val guard = ActionGuard(maxAttemptsOnOneScreen = 1, tripMs = 60_000)
        guard.check(Screen.REELS_TAB, t0)
        assertEquals(Verdict.JUST_STOPPED, guard.check(Screen.REELS_TAB, t0 + 1_000))
        assertFalse(guard.isStopped(t0 + 61_000))

        guard.reset()
        assertFalse(guard.isStopped(t0 + 2_000))
        assertEquals(Verdict.ACT, guard.check(Screen.REELS_TAB, t0 + 2_000))
    }
}
