package dev.ghazi.igfocus.policy

/** A move away from a blocked screen. */
enum class Steer {
    /** Tap the DMs tab. */
    OPEN_MESSAGES,

    /** Press Back. */
    BACK,
}

/** A part of an Instagram screen to hide under a cover, or a patch over a tab button. */
enum class Cover {
    /** Home, everything below the Stories row and above the tab bar. */
    HOME_FEED,

    /** Search, the grid below the search box. */
    EXPLORE_GRID,

    /** The Reels button in the tab bar, hidden so the bar reads Home, DMs, Search, Profile. */
    REELS_TAB_BUTTON,

    /**
     * Invisible patches over the Home and Search tab buttons. A press puts the Home or Explore
     * cover up at once, before Instagram has drawn the screen, and then passes the tap on.
     */
    HOME_TAB_BUTTON,
    SEARCH_TAB_BUTTON,
    ;

    /** Covers that hide Instagram content, as opposed to the small patches in the tab bar. */
    val hidesContent: Boolean get() = this == HOME_FEED || this == EXPLORE_GRID

    /** For a tab-button patch, the cover a press on it puts up. */
    val raises: Cover?
        get() = when (this) {
            HOME_TAB_BUTTON -> HOME_FEED
            SEARCH_TAB_BUTTON -> EXPLORE_GRID
            else -> null
        }
}

/**
 * What to do on the current screen. [covers] is the complete set that should be showing now:
 * anything not in it must be taken down, so a cover never outlives the screen it belongs to.
 */
data class Decision(val steer: Steer? = null, val covers: Set<Cover> = emptySet()) {
    companion object {
        val NONE = Decision()
    }
}

/** Which blocking rules are switched on. Everything is off until the user turns it on. */
data class BlockingSettings(
    val reelsTab: Boolean = false,
    val otherReels: Boolean = false,
    val searchResults: Boolean = false,
    val homeFeed: Boolean = false,
    val explore: Boolean = false,
    /** With [homeFeed] on: Instagram opening on Home goes to messages. Not a rule of its own. */
    val openOnMessages: Boolean = true,
) {
    val anyOn: Boolean get() = reelsTab || otherReels || searchResults || homeFeed || explore
}
