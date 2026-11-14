package dev.ghazi.igfocus.service

import dev.ghazi.igfocus.inspector.Capture
import dev.ghazi.igfocus.inspector.CaptureJson
import dev.ghazi.igfocus.inspector.ScreenSignature
import dev.ghazi.igfocus.rules.InstagramRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Replays the screens captured on the test phone (Instagram 455) through the classifier. */
class ScreenClassifierTest {

    private val expected = mapOf(
        "Home" to Screen.HOME,
        "Home shell while a story was opening" to Screen.HOME,
        "Reels tab" to Screen.REELS_TAB,
        "Reel opened from a DM" to Screen.DM_REEL,
        "Saved reel: before swipe" to Screen.REEL_VIEWER,
        "Saved reel: after swipe" to Screen.REEL_VIEWER,
        "Reel opened from search" to Screen.REEL_VIEWER,
        "Story" to Screen.STORY_VIEWER,
        "DM inbox" to Screen.DM_INBOX,
        "DM thread" to Screen.DM_THREAD,
        "Search / Explore" to Screen.SEARCH_EXPLORE,
        "Search results: name" to Screen.SEARCH_TYPING,
        "Search results: hashtag rows" to Screen.SEARCH_RESULTS,
        "Search result grid" to Screen.SEARCH_RESULTS,
        "Hashtag page" to Screen.SEARCH_RESULTS,
        "Saved" to Screen.SAVED,
        "Own profile" to Screen.PROFILE_OWN,
        "Other profile" to Screen.PROFILE_OTHER,
        "Other profile: Reels tab" to Screen.PROFILE_OTHER,
        "Create" to Screen.CREATE,
    )

    private fun fixtures(): List<Capture> {
        val stream = javaClass.classLoader!!.getResourceAsStream("fixtures/screens-ig455.json")
        assertNotNull("fixture file is missing", stream)
        return CaptureJson.decode(stream!!.bufferedReader().use { it.readText() })
    }

    @Test
    fun `every captured screen is classified as expected`() {
        val actual = fixtures().associate { it.label to ScreenClassifier.classify(ScreenSignature(it.ids)) }
        assertEquals(expected, actual)
    }

    /** The finding the whole design rests on: a DM reel shares the Reels tab's viewer. */
    @Test
    fun `a reel from a DM is never mistaken for the Reels tab`() {
        val dmReel = fixtures().first { it.label == "Reel opened from a DM" }
        val reelsTab = fixtures().first { it.label == "Reels tab" }
        assertTrue(InstagramRules.REEL_PAGER in dmReel.ids)
        assertTrue(InstagramRules.REEL_PAGER in reelsTab.ids)
        assertEquals(Screen.DM_REEL, ScreenClassifier.classify(ScreenSignature(dmReel.ids)))
        assertEquals(Screen.REELS_TAB, ScreenClassifier.classify(ScreenSignature(reelsTab.ids)))
    }

    /**
     * Own profile scrolled down loses its + button. The tab bar still marks it as the user's own, so
     * their own reels are not mistaken for someone else's. Not yet confirmed by a capture.
     */
    @Test
    fun `own profile without its + button but with the tab bar is still own profile`() {
        val own = fixtures().first { it.label == "Own profile" }
        val scrolled = own.ids - InstagramRules.OWN_PROFILE
        assertEquals(Screen.PROFILE_OWN, ScreenClassifier.classify(ScreenSignature(scrolled)))
    }

    /** On the phone each question is a lookup inside Instagram; a scan must stay a few of them. */
    @Test
    fun `recognising a screen asks about only a handful of IDs`() {
        for (capture in fixtures()) {
            val ids = capture.ids.toSet()
            val asked = mutableListOf<String>()
            val screen = ScreenClassifier.classify { id -> asked += id; id in ids }
            assertEquals(capture.label, ScreenClassifier.classify(ScreenSignature(capture.ids)), screen)
            assertTrue("${capture.label} asked ${asked.size}: $asked", asked.size <= 14)
        }
    }

    /** The shortcut must never change an answer: every capture, after every possible screen. */
    @Test
    fun `the shortcut from the previous screen gives the same answer for every capture`() {
        for (capture in fixtures()) {
            val ids = capture.ids.toSet()
            val full = ScreenClassifier.classify { it in ids }
            for (previous in Screen.entries) {
                assertEquals("${capture.label} after $previous", full, ScreenClassifier.classify(previous) { it in ids })
            }
        }
    }

    @Test
    fun `staying on Home or Explore takes three questions`() {
        for ((label, screen) in listOf("Home" to Screen.HOME, "Search / Explore" to Screen.SEARCH_EXPLORE)) {
            val ids = fixtures().first { it.label == label }.ids.toSet()
            val asked = mutableListOf<String>()
            assertEquals(screen, ScreenClassifier.classify(screen) { id -> asked += id; id in ids })
            assertEquals("$label asked $asked", 3, asked.size)
        }
    }

    @Test
    fun `an unrecognised or empty screen is UNKNOWN`() {
        assertEquals(Screen.UNKNOWN, ScreenClassifier.classify(ScreenSignature(emptyList())))
        assertEquals(
            Screen.UNKNOWN,
            ScreenClassifier.classify(ScreenSignature(listOf("action_bar_root", "android:id/content"))),
        )
    }
}
