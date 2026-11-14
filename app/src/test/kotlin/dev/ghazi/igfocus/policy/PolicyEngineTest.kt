package dev.ghazi.igfocus.policy

import dev.ghazi.igfocus.inspector.CaptureJson
import dev.ghazi.igfocus.inspector.ScreenSignature
import dev.ghazi.igfocus.service.Screen
import dev.ghazi.igfocus.service.ScreenClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyEngineTest {

    private val allOn = BlockingSettings(
        reelsTab = true,
        otherReels = true,
        searchResults = true,
        homeFeed = true,
        explore = true,
    )

    /** Records [path] one second apart. */
    private fun historyAfter(vararg path: Screen): NavigationHistory {
        val history = NavigationHistory()
        path.forEachIndexed { i, screen -> history.record(screen, i * 1_000L) }
        return history
    }

    private fun decideAfter(vararg path: Screen, settings: BlockingSettings = allOn, paused: Boolean = false): Decision =
        PolicyEngine.decide(path.last(), historyAfter(*path), settings, paused)

    private fun steerAfter(vararg path: Screen, settings: BlockingSettings = allOn): Steer? =
        decideAfter(*path, settings = settings).steer

    /** Covers over Instagram content, leaving out the small Reels-button patch. */
    private fun contentCoversAfter(vararg path: Screen, settings: BlockingSettings = allOn): Set<Cover> =
        decideAfter(*path, settings = settings).covers.filter { it.hidesContent }.toSet()

    private val alwaysKept = listOf(
        Screen.DM_INBOX, Screen.DM_THREAD, Screen.STORY_VIEWER, Screen.SEARCH_TYPING,
        Screen.SAVED, Screen.PROFILE_OWN, Screen.PROFILE_OTHER, Screen.CREATE, Screen.UNKNOWN,
    )

    @Test
    fun `the DM inbox and chats never get a steer or a content cover, from any screen, with every rule on`() {
        for (dm in listOf(Screen.DM_INBOX, Screen.DM_THREAD)) {
            for (from in Screen.entries) {
                assertNull("$dm from $from", steerAfter(from, dm))
                assertEquals("$dm from $from", emptySet<Cover>(), contentCoversAfter(from, dm))
            }
        }
    }

    @Test
    fun `a reel a friend sent plays, from any screen, with every rule on`() {
        for (from in Screen.entries) {
            assertNull("DM_REEL from $from", steerAfter(from, Screen.DM_REEL))
            assertEquals(emptySet<Cover>(), contentCoversAfter(from, Screen.DM_REEL))
        }
    }

    @Test
    fun `screens that are always kept get no steer and no content cover`() {
        for (screen in alwaysKept) {
            assertNull("$screen", steerAfter(Screen.HOME, screen))
            assertEquals("$screen", emptySet<Cover>(), contentCoversAfter(Screen.HOME, screen))
        }
    }

    @Test
    fun `the Reels button is hidden on every recognised screen while the Reels rule is on`() {
        for (screen in Screen.entries - Screen.UNKNOWN) {
            assertTrue("$screen", Cover.REELS_TAB_BUTTON in decideAfter(Screen.HOME, screen).covers)
        }
        assertTrue(Cover.REELS_TAB_BUTTON !in decideAfter(Screen.HOME, Screen.UNKNOWN).covers)
        assertTrue(Cover.REELS_TAB_BUTTON !in decideAfter(Screen.HOME, settings = allOn.copy(reelsTab = false)).covers)
    }

    @Test
    fun `the Home and Search tab buttons get a press patch, except on their own screen`() {
        for (screen in Screen.entries - Screen.UNKNOWN) {
            val covers = decideAfter(Screen.SAVED, screen).covers
            assertEquals("$screen", screen != Screen.HOME, Cover.HOME_TAB_BUTTON in covers)
            assertEquals("$screen", screen != Screen.SEARCH_EXPLORE, Cover.SEARCH_TAB_BUTTON in covers)
        }
        assertTrue(decideAfter(Screen.SAVED, Screen.UNKNOWN).covers.none { it.raises != null })
    }

    @Test
    fun `each press patch follows its own cover rule`() {
        val homeOnly = BlockingSettings(homeFeed = true)
        assertEquals(setOf(Cover.HOME_TAB_BUTTON), decideAfter(Screen.HOME, Screen.DM_INBOX, settings = homeOnly).covers)
        val exploreOnly = BlockingSettings(explore = true)
        assertEquals(setOf(Cover.SEARCH_TAB_BUTTON), decideAfter(Screen.HOME, Screen.DM_INBOX, settings = exploreOnly).covers)
    }

    @Test
    fun `a press patch raises the cover of its own screen`() {
        assertEquals(Cover.HOME_FEED, Cover.HOME_TAB_BUTTON.raises)
        assertEquals(Cover.EXPLORE_GRID, Cover.SEARCH_TAB_BUTTON.raises)
        for (cover in Cover.entries.filter { it.raises != null }) assertTrue(!cover.hidesContent)
    }

    @Test
    fun `paused does nothing anywhere`() {
        for (screen in Screen.entries) {
            assertEquals("$screen", Decision.NONE, decideAfter(Screen.SEARCH_TYPING, screen, paused = true))
        }
    }

    @Test
    fun `every rule is off by default`() {
        for (screen in Screen.entries) {
            assertEquals("$screen", Decision.NONE, decideAfter(Screen.SEARCH_TYPING, screen, settings = BlockingSettings()))
        }
    }

    @Test
    fun `the Reels tab steers to messages, and only when its rule is on`() {
        assertEquals(Steer.OPEN_MESSAGES, steerAfter(Screen.HOME, Screen.REELS_TAB))
        assertNull(steerAfter(Screen.HOME, Screen.REELS_TAB, settings = allOn.copy(reelsTab = false)))
    }

    @Test
    fun `a reel opened from Saved, own profile or a chat is allowed`() {
        for (entry in PolicyEngine.ALLOWED_REEL_ENTRIES) {
            assertNull("$entry", steerAfter(entry, Screen.REEL_VIEWER))
        }
    }

    @Test
    fun `the brief flicker to UNKNOWN does not hide where a reel was opened from`() {
        assertNull(steerAfter(Screen.SAVED, Screen.UNKNOWN, Screen.REEL_VIEWER))
        assertEquals(Steer.BACK, steerAfter(Screen.SEARCH_TYPING, Screen.UNKNOWN, Screen.REEL_VIEWER))
    }

    @Test
    fun `a reel opened from anywhere else, or from an unknown place, goes Back`() {
        for (entry in listOf(Screen.SEARCH_TYPING, Screen.SEARCH_RESULTS, Screen.PROFILE_OTHER, Screen.HOME, Screen.REELS_TAB)) {
            assertEquals("$entry", Steer.BACK, steerAfter(entry, Screen.REEL_VIEWER))
        }
        assertEquals(Steer.BACK, steerAfter(Screen.REEL_VIEWER))
    }

    /** The bypass found on 14 Nov: swipe into Search and tap a reel before the grid is covered. */
    @Test
    fun `a reel tapped on Explore goes Back`() {
        assertEquals(Steer.BACK, steerAfter(Screen.DM_INBOX, Screen.SEARCH_EXPLORE, Screen.REEL_VIEWER))
    }

    @Test
    fun `a reel opened straight from the DM inbox goes Back, since the inbox is next to Search`() {
        assertEquals(Steer.BACK, steerAfter(Screen.DM_INBOX, Screen.REEL_VIEWER))
    }

    @Test
    fun `passing through Saved for a moment does not count as opening a reel from it`() {
        val history = NavigationHistory()
        history.record(Screen.SEARCH_EXPLORE, 0)
        history.record(Screen.SAVED, 1_000)
        history.record(Screen.REEL_VIEWER, 1_200)
        assertEquals(Steer.BACK, PolicyEngine.decide(Screen.REEL_VIEWER, history, allOn, paused = false).steer)
    }

    @Test
    fun `swiping on from a reel a friend sent goes Back`() {
        assertEquals(Steer.BACK, steerAfter(Screen.DM_THREAD, Screen.DM_REEL, Screen.REEL_VIEWER))
    }

    /** The gap found in testing on 14 Nov: open a saved reel, swipe up, and recommendations follow. */
    @Test
    fun `a saved reel plays, and swiping to the next reel goes Back`() {
        val history = historyAfter(Screen.SAVED, Screen.REEL_VIEWER)
        history.onReelPagerScroll(0, 0, SWIPE)
        assertNull(PolicyEngine.decide(Screen.REEL_VIEWER, history, allOn, paused = false).steer)
        history.onReelPagerScroll(1, 0, SWIPE)
        assertEquals(Steer.BACK, PolicyEngine.decide(Screen.REEL_VIEWER, history, allOn, paused = false).steer)
    }

    @Test
    fun `a reel a friend sent plays, and swiping to the next reel goes Back`() {
        val history = historyAfter(Screen.DM_THREAD, Screen.DM_REEL)
        history.onReelPagerScroll(0, 0, SWIPE)
        history.onReelPagerScroll(0, 0, SWIPE)
        assertNull(PolicyEngine.decide(Screen.DM_REEL, history, allOn, paused = false).steer)
        history.onReelPagerScroll(1, 0, SWIPE)
        assertEquals(Steer.BACK, PolicyEngine.decide(Screen.DM_REEL, history, allOn, paused = false).steer)
    }

    @Test
    fun `one reel at a time follows the Reels switch`() {
        val history = historyAfter(Screen.SAVED, Screen.REEL_VIEWER)
        history.onReelPagerScroll(0, 0, SWIPE)
        history.onReelPagerScroll(1, 0, SWIPE)
        val off = allOn.copy(reelsTab = false, otherReels = false)
        assertNull(PolicyEngine.decide(Screen.REEL_VIEWER, history, off, paused = false).steer)
    }

    @Test
    fun `result pages go Back and covers are asked for on Home and Explore`() {
        assertEquals(Steer.BACK, steerAfter(Screen.SEARCH_TYPING, Screen.SEARCH_RESULTS))
        assertEquals(setOf(Cover.HOME_FEED), contentCoversAfter(Screen.HOME))
        assertEquals(setOf(Cover.EXPLORE_GRID), contentCoversAfter(Screen.SEARCH_EXPLORE))
    }

    @Test
    fun `with only the Reels rule on, nothing but Reels is acted on`() {
        val reelsOnly = BlockingSettings(reelsTab = true, otherReels = true)
        for (screen in Screen.entries - Screen.REELS_TAB - Screen.REEL_VIEWER) {
            assertNull("$screen", steerAfter(Screen.SAVED, screen, settings = reelsOnly))
            assertEquals("$screen", emptySet<Cover>(), contentCoversAfter(Screen.SAVED, screen, settings = reelsOnly))
        }
    }

    @Test
    fun `Instagram opening on Home goes to messages, with the feed still covered`() {
        val decision = decideAfter(Screen.HOME)
        assertEquals(Steer.OPEN_MESSAGES, decision.steer)
        assertTrue(Cover.HOME_FEED in decision.covers)
    }

    @Test
    fun `with open-on-messages switched off, Instagram opening on Home stays there, covered`() {
        val decision = decideAfter(Screen.HOME, settings = allOn.copy(openOnMessages = false))
        assertNull(decision.steer)
        assertTrue(Cover.HOME_FEED in decision.covers)
    }

    @Test
    fun `pressing Home after Instagram has opened stays on Home`() {
        assertNull(steerAfter(Screen.HOME, Screen.DM_INBOX, Screen.HOME))
        assertNull(steerAfter(Screen.DM_INBOX, Screen.HOME))
    }

    @Test
    fun `Instagram opening anywhere else is left there`() {
        for (screen in Screen.entries - Screen.HOME - Screen.REELS_TAB - Screen.REEL_VIEWER - Screen.SEARCH_RESULTS) {
            assertNull("$screen", steerAfter(screen))
        }
    }

    @Test
    fun `opening on messages follows the Home feed switch`() {
        assertNull(steerAfter(Screen.HOME, settings = allOn.copy(homeFeed = false)))
    }

    @Test
    fun `coming back to Instagram on Home goes to messages again`() {
        val history = historyAfter(Screen.HOME, Screen.DM_INBOX, Screen.HOME)
        history.onInstagramLeft()
        history.record(Screen.HOME, 10_000)
        assertEquals(Steer.OPEN_MESSAGES, PolicyEngine.decide(Screen.HOME, history, allOn, paused = false).steer)
    }

    /** A cover must not outlive its screen: opening a story from Home asks for no feed cover. */
    @Test
    fun `leaving Home for a story asks for no feed cover`() {
        assertEquals(emptySet<Cover>(), contentCoversAfter(Screen.HOME, Screen.STORY_VIEWER))
    }

    /** The captured DM screens from the test phone, classified and decided with every rule on. */
    @Test
    fun `captured DM screens get no steer and no content cover`() {
        val stream = javaClass.classLoader!!.getResourceAsStream("fixtures/screens-ig455.json")!!
        val captures = CaptureJson.decode(stream.bufferedReader().use { it.readText() })
        val dm = captures.filter { it.label in setOf("DM inbox", "DM thread", "Reel opened from a DM") }
        assertEquals(3, dm.size)
        for (capture in dm) {
            val screen = ScreenClassifier.classify(ScreenSignature(capture.ids))
            assertNull(capture.label, steerAfter(Screen.DM_THREAD, screen))
            assertEquals(capture.label, emptySet<Cover>(), contentCoversAfter(Screen.DM_THREAD, screen))
        }
    }

    private companion object {
        /** 64 dp on the S25 Ultra. */
        const val SWIPE = 224
    }
}
