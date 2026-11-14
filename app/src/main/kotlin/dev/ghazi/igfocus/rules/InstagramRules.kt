package dev.ghazi.igfocus.rules

/**
 * Everything that is specific to Instagram's app lives in this file and nowhere else.
 * When an Instagram update renames a view, this is the only file to change.
 *
 * The IDs below were measured on a Galaxy S25 Ultra with Instagram 455.0.0.51.72 (13 Nov 2026).
 * The captures they come from are in app/src/test/resources/fixtures/screens-ig455.json, and
 * ScreenClassifierTest replays them.
 */
object InstagramRules {
    const val PACKAGE = "com.instagram.android"

    private const val ID_PREFIX = "$PACKAGE:id/"

    /** `com.instagram.android:id/tab_bar` → `tab_bar`. IDs from other packages are kept whole. */
    fun shortId(fullId: String): String = fullId.removePrefix(ID_PREFIX)

    /** `tab_bar` → `com.instagram.android:id/tab_bar`, for looking a view up by ID. */
    fun fullId(shortId: String): String = ID_PREFIX + shortId

    /** The bottom bar. Shown on the five tab screens, absent on screens pushed on top of them. */
    const val TAB_BAR = "tab_bar"

    /** The DMs tab in the bottom bar: what "steer to messages" taps. */
    const val DM_TAB = "direct_tab"

    /** The Home tab. Tapping it while on Home scrolls the feed back to the top. */
    const val HOME_TAB = "feed_tab"

    /** The Search tab, which opens Search with the Explore grid. */
    const val SEARCH_TAB = "search_tab"

    /** The vertical pager shared by every reel viewer: Reels tab, DM, Saved, search, profiles. */
    const val REEL_PAGER = "clips_viewer_view_pager"

    /**
     * Another reel pager ID that a different blocker (AwayDoomscrollin) found in use. Not seen on
     * the test phone yet, so it only gets logged when it appears; promote it to the classifier
     * once a capture shows where it occurs.
     */
    const val REEL_PAGER_OTHER = "clips_view_pager"

    /** Scroll events from these are the reel pager moving to another reel. */
    val REEL_PAGERS = setOf(REEL_PAGER, REEL_PAGER_OTHER)

    /** The Reels button in the tab bar (content description "Reels"). */
    const val REELS_TAB_BUTTON = "clips_tab"

    /** The reply box under a reel that was opened from a DM. Either one marks a DM reel. */
    val DM_REEL_MARKERS = setOf("reply_bar_container", "reel_viewer_message_composer")

    /** Despite the name, this is the Stories viewer, not Reels. */
    const val STORY_VIEWER = "reel_viewer_root"

    const val DM_THREAD = "thread_view_root"
    const val DM_INBOX = "direct_inbox_action_bar"

    /** The gallery picker that Create (+) opens. Camera and editor screens are not measured yet. */
    const val CREATE_GALLERY = "gallery_picker_view"

    const val SAVED = "saved_tab_layout"

    const val EXPLORE = "explore_action_bar"

    /** The tab strip on result pages (For you, Accounts, Tags, …), including hashtag pages. */
    const val SEARCH_RESULT_TABS = "search_tab_bar_layout"

    /** The search box. On its own (no Explore bar, no result tabs) the user is typing a query. */
    const val SEARCH_BOX = "action_bar_search_edit_text"

    /** Present on every profile, own or not, at any scroll position measured so far. */
    const val PROFILE = "profile_header_container"

    /**
     * The + button that only the user's own profile header has. It scrolls away, so the classifier
     * also counts a profile with the tab bar as the user's own: other profiles are pushed screens,
     * which have no tab bar.
     */
    const val OWN_PROFILE = "profile_header_create_button"

    /** Home's top bar (+, logo, notifications). It slides away when the feed scrolls. */
    const val HOME_ACTION_BAR = "main_feed_action_bar"

    /**
     * Home's whole feed list, from under the top bar to the tab bar ([0,128][1440,2884] on the
     * S25 Ultra). Others described it as the Stories row; measured, it is the list that holds the
     * Stories tray and the posts, so dragging on the tray scrolls the feed.
     */
    const val HOME_FEED_LIST = "sticky_header_list"

    /** The Stories tray at the top of [HOME_FEED_LIST] ([0,338][1440,803] at rest). */
    const val HOME_STORIES_TRAY = "reels_tray_container"

    /** The search bar area on the Search tab, which may include the topic pills below it. */
    const val EXPLORE_BAR_CONTAINER = "explore_action_bar_container"

    /** Which edge of a view a cover starts from. */
    enum class Edge { TOP, BOTTOM }

    data class Anchor(val id: String, val edge: Edge)

    /**
     * Where the Home feed cover starts, best first; the first one that is on screen and leaves
     * room above the tab bar wins. Below the Stories tray while it shows. Once the feed is
     * scrolled and the tray is gone, below the top bar, or the top of the list once the bar has
     * slid away too, so scrolling never uncovers posts.
     */
    val HOME_FEED_TOP_ANCHORS = listOf(
        Anchor(HOME_STORIES_TRAY, Edge.BOTTOM),
        Anchor(HOME_ACTION_BAR, Edge.BOTTOM),
        Anchor(HOME_FEED_LIST, Edge.TOP),
    )

    /** Where the Explore grid cover starts, best first: below the search bar. */
    val EXPLORE_GRID_TOP_ANCHORS = listOf(
        Anchor(EXPLORE_BAR_CONTAINER, Edge.BOTTOM),
        Anchor(EXPLORE, Edge.BOTTOM),
    )
}
