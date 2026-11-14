package dev.ghazi.igfocus.policy

/** A rectangle in screen pixels. Android's Rect cannot be used in plain unit tests. */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    override fun toString() = "[$left,$top][$right,$bottom]"
}

object CoverGeometry {
    /**
     * A cover from the first usable top edge down to the top of the tab bar, across the tab bar's
     * width. [topEdges] are y positions in screen pixels, best first, null where the view is not
     * on screen. An edge is usable if it leaves at least [minHeight] above the tab bar.
     *
     * Fails open: with no tab bar, or no usable edge, there is no cover. It never falls back to
     * covering the whole screen, which could hide the tabs and trap the user on the screen.
     */
    fun between(topEdges: List<Int?>, tabBar: Box?, minHeight: Int): Box? {
        if (tabBar == null || tabBar.width <= 0 || tabBar.top <= 0) return null
        val top = topEdges.firstOrNull { it != null && tabBar.top - it >= minHeight } ?: return null
        return Box(tabBar.left, top, tabBar.right, tabBar.top)
    }

    /**
     * Whether Home's feed has been scrolled up from rest: the Stories tray or the top bar has left
     * the screen, or the tray sits higher than straight under the top bar. Pull-to-refresh moves
     * the tray down instead, and does not count.
     */
    fun isFeedScrolledUp(storiesTray: Box?, topBar: Box?, slop: Int): Boolean =
        storiesTray == null || topBar == null || storiesTray.top < topBar.bottom - slop
}
