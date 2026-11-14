package dev.ghazi.igfocus.policy

import dev.ghazi.igfocus.service.Screen

/**
 * Screen + where it was reached from + settings → what to do. No Android code, so every rule is
 * unit-tested against the screens captured on the test phone.
 *
 * Never acted on, whatever the settings: the DM inbox and chats, Create, Stories (until a later
 * update), Saved, profiles, the search box while typing, and anything unrecognised. The only
 * thing those screens may get is the small patches over buttons in the tab bar.
 */
object PolicyEngine {

    /**
     * A reel viewer opened from one of these is allowed: Saved, own profile, a chat. The DM inbox
     * is not on the list: it is a tab next to Search, and swiping from it into Search and tapping
     * a reel at once would otherwise count as "opened from DMs".
     */
    val ALLOWED_REEL_ENTRIES = setOf(Screen.SAVED, Screen.PROFILE_OWN, Screen.DM_THREAD)

    /** An allowed entry counts only if the user was on it at least this long before the reel opened. */
    const val MIN_ENTRY_STAY_MS = 400L

    private fun reelAllowed(history: NavigationHistory): Boolean =
        history.reelEntry in ALLOWED_REEL_ENTRIES && history.reelEntryStayMs >= MIN_ENTRY_STAY_MS

    fun decide(screen: Screen, history: NavigationHistory, settings: BlockingSettings, paused: Boolean): Decision {
        if (paused) return Decision.NONE
        val decision = contentDecision(screen, history, settings)
        if (screen == Screen.UNKNOWN) return decision
        // Patches go wherever the tab bar shows; with no bar on screen, there is nothing to patch.
        val patches = buildSet {
            if (settings.reelsTab) add(Cover.REELS_TAB_BUTTON)
            // Pressing Home or Search puts its cover up at once. Not needed once already there.
            if (settings.homeFeed && screen != Screen.HOME) add(Cover.HOME_TAB_BUTTON)
            if (settings.explore && screen != Screen.SEARCH_EXPLORE) add(Cover.SEARCH_TAB_BUTTON)
        }
        return decision.copy(covers = decision.covers + patches)
    }

    private fun contentDecision(screen: Screen, history: NavigationHistory, settings: BlockingSettings): Decision =
        when (screen) {
            Screen.REELS_TAB ->
                if (settings.reelsTab) Decision(steer = Steer.OPEN_MESSAGES) else Decision.NONE
            // One reel at a time: an allowed reel plays, and swiping on to another one goes Back.
            Screen.REEL_VIEWER ->
                if (settings.otherReels && (!reelAllowed(history) || history.reelMovedOn)) {
                    Decision(steer = Steer.BACK)
                } else {
                    Decision.NONE
                }
            Screen.DM_REEL ->
                if (settings.otherReels && history.reelMovedOn) Decision(steer = Steer.BACK) else Decision.NONE
            Screen.SEARCH_RESULTS ->
                if (settings.searchResults) Decision(steer = Steer.BACK) else Decision.NONE
            // Instagram opens on messages: Home is only Stories and a cover, so landing there is
            // pointless. A press on Home afterwards is the user's own choice and stays.
            Screen.HOME -> when {
                !settings.homeFeed -> Decision.NONE
                history.onOpeningScreen && settings.openOnMessages -> Decision(steer = Steer.OPEN_MESSAGES, covers = setOf(Cover.HOME_FEED))
                else -> Decision(covers = setOf(Cover.HOME_FEED))
            }
            Screen.SEARCH_EXPLORE ->
                if (settings.explore) Decision(covers = setOf(Cover.EXPLORE_GRID)) else Decision.NONE
            Screen.DM_INBOX,
            Screen.DM_THREAD,
            Screen.STORY_VIEWER,
            Screen.SEARCH_TYPING,
            Screen.SAVED,
            Screen.PROFILE_OWN,
            Screen.PROFILE_OTHER,
            Screen.CREATE,
            Screen.UNKNOWN -> Decision.NONE
        }
}
