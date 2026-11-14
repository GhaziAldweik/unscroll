package dev.ghazi.igfocus.service

import dev.ghazi.igfocus.inspector.ScreenSignature
import dev.ghazi.igfocus.rules.InstagramRules

enum class Screen {
    HOME,
    REELS_TAB,

    /** A reel opened from a DM. Must never be treated like the Reels tab. */
    DM_REEL,

    /** Any other reel viewer: from Saved, search, a profile, a post or a link. */
    REEL_VIEWER,
    STORY_VIEWER,
    DM_INBOX,
    DM_THREAD,
    SEARCH_EXPLORE,

    /** The search box is open and suggestions are listed. */
    SEARCH_TYPING,

    /** A tabbed result page. Hashtag pages are this screen too. */
    SEARCH_RESULTS,
    SAVED,
    PROFILE_OWN,
    PROFILE_OTHER,
    CREATE,

    /** Nothing recognised. Later phases must take no action on this. */
    UNKNOWN,
}

object ScreenClassifier {

    fun classify(signature: ScreenSignature): Screen {
        val ids = signature.ids.toSet()
        return classify { it in ids }
    }

    /**
     * Like [classify] below, with a shortcut while [previous] is Home or Search: once a reel or a
     * story on top is ruled out, that screen's own marker confirms it is still showing. That is 3
     * questions instead of up to 11, so the scans that keep a cover in place, and that notice a
     * story opening, run far more often. In every capture, Home's and Explore's markers appear
     * on no other screen, so the answer is the same either way (ScreenClassifierTest).
     */
    fun classify(previous: Screen, isVisible: (String) -> Boolean): Screen {
        val marker = when (previous) {
            Screen.HOME -> InstagramRules.HOME_ACTION_BAR
            Screen.SEARCH_EXPLORE -> InstagramRules.EXPLORE
            else -> null
        }
        val stillThere = marker != null &&
            !isVisible(InstagramRules.REEL_PAGER) &&
            !isVisible(InstagramRules.STORY_VIEWER) &&
            isVisible(marker)
        return if (stillThere) previous else classify(isVisible)
    }

    /**
     * [isVisible] answers whether a view with that ID is on screen. Only the IDs a decision needs
     * are asked about, in this order, so on the phone each question can be one cheap lookup
     * instead of reading Instagram's whole screen. The order matters: most specific first.
     */
    fun classify(isVisible: (String) -> Boolean): Screen = when {
        isVisible(InstagramRules.REEL_PAGER) -> when {
            InstagramRules.DM_REEL_MARKERS.any(isVisible) -> Screen.DM_REEL
            isVisible(InstagramRules.TAB_BAR) -> Screen.REELS_TAB
            else -> Screen.REEL_VIEWER
        }
        isVisible(InstagramRules.STORY_VIEWER) -> Screen.STORY_VIEWER
        isVisible(InstagramRules.DM_THREAD) -> Screen.DM_THREAD
        isVisible(InstagramRules.DM_INBOX) -> Screen.DM_INBOX
        isVisible(InstagramRules.CREATE_GALLERY) -> Screen.CREATE
        isVisible(InstagramRules.SAVED) -> Screen.SAVED
        isVisible(InstagramRules.EXPLORE) -> Screen.SEARCH_EXPLORE
        isVisible(InstagramRules.SEARCH_RESULT_TABS) -> Screen.SEARCH_RESULTS
        isVisible(InstagramRules.SEARCH_BOX) -> Screen.SEARCH_TYPING
        isVisible(InstagramRules.PROFILE) ->
            if (isVisible(InstagramRules.OWN_PROFILE) || isVisible(InstagramRules.TAB_BAR)) {
                Screen.PROFILE_OWN
            } else {
                Screen.PROFILE_OTHER
            }
        isVisible(InstagramRules.HOME_ACTION_BAR) -> Screen.HOME
        // Home's shell without its header, seen while a story was opening.
        isVisible(InstagramRules.TAB_BAR) && isVisible(InstagramRules.HOME_FEED_LIST) -> Screen.HOME
        else -> Screen.UNKNOWN
    }
}
