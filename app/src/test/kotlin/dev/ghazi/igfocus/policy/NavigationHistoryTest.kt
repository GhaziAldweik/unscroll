package dev.ghazi.igfocus.policy

import dev.ghazi.igfocus.service.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationHistoryTest {

    @Test
    fun `UNKNOWN is never recorded`() {
        val history = NavigationHistory()
        history.record(Screen.HOME, 0)
        history.record(Screen.UNKNOWN, 100)
        assertEquals(Screen.HOME, history.current)
    }

    @Test
    fun `the way into the reel viewer is kept while swiping inside it`() {
        val history = NavigationHistory()
        history.record(Screen.SAVED, 0)
        history.record(Screen.REEL_VIEWER, 3_000)
        history.record(Screen.UNKNOWN, 4_000)
        history.record(Screen.REEL_VIEWER, 5_000)
        assertEquals(Screen.SAVED, history.reelEntry)
        assertEquals(3_000, history.reelEntryStayMs)
    }

    @Test
    fun `the stay on the way in is measured from when that screen was first seen`() {
        val history = NavigationHistory()
        history.record(Screen.DM_INBOX, 0)
        history.record(Screen.SEARCH_EXPLORE, 1_000)
        history.record(Screen.REEL_VIEWER, 1_150)
        assertEquals(Screen.SEARCH_EXPLORE, history.reelEntry)
        assertEquals(150, history.reelEntryStayMs)
    }

    @Test
    fun `the way in is forgotten on leaving the reel viewer`() {
        val history = NavigationHistory()
        history.record(Screen.SAVED, 0)
        history.record(Screen.REEL_VIEWER, 1_000)
        history.record(Screen.SAVED, 2_000)
        assertNull(history.reelEntry)
    }

    @Test
    fun `a reel showing when the service starts has no known way in`() {
        val history = NavigationHistory()
        history.record(Screen.REEL_VIEWER, 1_000)
        assertNull(history.reelEntry)
    }

    @Test
    fun `the reel pager's first position is the start, and any other position means moved on`() {
        val history = NavigationHistory()
        history.record(Screen.SAVED, 0)
        history.record(Screen.REEL_VIEWER, 1_000)
        history.onReelPagerScroll(0, 0, SWIPE)
        history.onReelPagerScroll(0, 0, SWIPE)
        assertFalse(history.reelMovedOn)
        history.onReelPagerScroll(1, 0, SWIPE)
        assertTrue(history.reelMovedOn)
        // Swiping back to the first reel does not undo it: the latch holds for the visit.
        history.onReelPagerScroll(0, 0, SWIPE)
        assertTrue(history.reelMovedOn)
    }

    @Test
    fun `unknown positions and scrolls outside a reel viewer are ignored`() {
        val history = NavigationHistory()
        history.record(Screen.HOME, 0)
        history.onReelPagerScroll(0, 0, SWIPE)
        history.record(Screen.REEL_VIEWER, 1_000)
        history.onReelPagerScroll(-1, 0, SWIPE)
        history.onReelPagerScroll(3, 0, SWIPE)
        history.onReelPagerScroll(-1, 0, SWIPE)
        assertFalse(history.reelMovedOn)
    }

    @Test
    fun `leaving the reel viewer starts the next visit fresh, but a flicker does not`() {
        val history = NavigationHistory()
        history.record(Screen.SAVED, 0)
        history.record(Screen.REEL_VIEWER, 1_000)
        history.onReelPagerScroll(0, 0, SWIPE)
        history.onReelPagerScroll(1, 0, SWIPE)
        history.record(Screen.UNKNOWN, 2_000)
        assertTrue(history.reelMovedOn)
        history.record(Screen.SAVED, 3_000)
        assertFalse(history.reelMovedOn)
        history.record(Screen.REEL_VIEWER, 5_000)
        history.onReelPagerScroll(4, 0, SWIPE)
        assertFalse(history.reelMovedOn)
    }

    /** 14 Nov: a swipe right after opening reported the next reel in its very first event. */
    @Test
    fun `a big move of the reel pager counts as moving on even before any position is known`() {
        val history = NavigationHistory()
        history.record(Screen.SAVED, 0)
        history.record(Screen.REEL_VIEWER, 1_000)
        history.onReelPagerScroll(1, deltaY = 1_738, minSwipePx = SWIPE)
        assertTrue(history.reelMovedOn)
    }

    @Test
    fun `the reel opening and small moves do not count as moving on`() {
        val history = NavigationHistory()
        history.record(Screen.SAVED, 0)
        history.record(Screen.REEL_VIEWER, 1_000)
        history.onReelPagerScroll(0, deltaY = 0, minSwipePx = SWIPE)
        history.onReelPagerScroll(0, deltaY = 40, minSwipePx = SWIPE)
        history.onReelPagerScroll(-1, deltaY = -SWIPE + 1, minSwipePx = SWIPE)
        assertFalse(history.reelMovedOn)
    }

    /** 14 Nov: a collection inside Saved is not recognised, and every reel opened there bounced. */
    @Test
    fun `a reel opened again after a while on an unrecognised page starts a new visit from the same place`() {
        val history = NavigationHistory()
        history.record(Screen.SAVED, 0)
        history.record(Screen.REEL_VIEWER, 1_000)
        history.onReelPagerScroll(0, 0, SWIPE)
        history.onReelPagerScroll(1, 2_010, SWIPE)
        assertTrue(history.reelMovedOn)
        history.record(Screen.UNKNOWN, 2_000)
        history.record(Screen.REEL_VIEWER, 2_000 + NavigationHistory.REOPEN_AFTER_MS)
        assertFalse(history.reelMovedOn)
        assertEquals(Screen.SAVED, history.reelEntry)
        history.onReelPagerScroll(0, 0, SWIPE)
        history.onReelPagerScroll(1, 0, SWIPE)
        assertTrue(history.reelMovedOn)
    }

    @Test
    fun `a short flicker to UNKNOWN inside the reel viewer keeps the visit`() {
        val history = NavigationHistory()
        history.record(Screen.SAVED, 0)
        history.record(Screen.REEL_VIEWER, 1_000)
        history.onReelPagerScroll(0, 0, SWIPE)
        history.onReelPagerScroll(1, 0, SWIPE)
        history.record(Screen.UNKNOWN, 2_000)
        history.record(Screen.REEL_VIEWER, 2_150)
        assertTrue(history.reelMovedOn)
    }

    @Test
    fun `the first screen after Instagram opens is the opening screen until the user moves`() {
        val history = NavigationHistory()
        history.record(Screen.UNKNOWN, 0)
        history.record(Screen.HOME, 100)
        assertTrue(history.onOpeningScreen)
        history.record(Screen.HOME, 500)
        assertTrue(history.onOpeningScreen)
        history.record(Screen.DM_INBOX, 1_000)
        assertFalse(history.onOpeningScreen)
        // Pressing Home afterwards is the user's own choice.
        history.record(Screen.HOME, 2_000)
        assertFalse(history.onOpeningScreen)
    }

    @Test
    fun `coming back to Instagram makes the screen it shows the opening screen again`() {
        val history = NavigationHistory()
        history.record(Screen.DM_INBOX, 0)
        history.record(Screen.HOME, 1_000)
        assertFalse(history.onOpeningScreen)
        history.onInstagramLeft()
        history.record(Screen.UNKNOWN, 5_000)
        assertFalse(history.onOpeningScreen)
        history.record(Screen.HOME, 5_100)
        assertTrue(history.onOpeningScreen)
    }

    @Test
    fun `giving up on the opening screen keeps the user where they are`() {
        val history = NavigationHistory()
        history.record(Screen.HOME, 0)
        history.forgetOpeningScreen()
        assertFalse(history.onOpeningScreen)
        history.record(Screen.HOME, 1_000)
        assertFalse(history.onOpeningScreen)
    }

    @Test
    fun `reset forgets everything`() {
        val history = NavigationHistory()
        history.record(Screen.SAVED, 0)
        history.record(Screen.REEL_VIEWER, 1_000)
        history.reset()
        assertEquals(Screen.UNKNOWN, history.current)
        assertNull(history.reelEntry)
    }

    private companion object {
        /** 64 dp on the S25 Ultra. */
        const val SWIPE = 224
    }
}
