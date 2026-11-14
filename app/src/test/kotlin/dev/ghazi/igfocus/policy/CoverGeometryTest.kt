package dev.ghazi.igfocus.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pixel positions are those measured on the S25 Ultra's Home screen (1440 x 3120). */
class CoverGeometryTest {

    private val tabBar = Box(0, 2884, 1440, 3064)
    private val minHeight = 420

    // The Home anchors, best first: Stories tray bottom, top bar bottom, feed list top.
    private val trayBottom = 803
    private val actionBarBottom = 338
    private val listTop = 128

    @Test
    fun `at rest the cover runs from below the Stories tray to the tab bar, full width`() {
        assertEquals(
            Box(0, 803, 1440, 2884),
            CoverGeometry.between(listOf(trayBottom, actionBarBottom, listTop), tabBar, minHeight),
        )
    }

    @Test
    fun `scrolled so the tray is gone, the cover starts below the top bar`() {
        assertEquals(
            Box(0, 338, 1440, 2884),
            CoverGeometry.between(listOf(null, actionBarBottom, listTop), tabBar, minHeight),
        )
    }

    /** Measured 14 Nov: tray and top bar both gone, which left the feed uncovered before. */
    @Test
    fun `scrolled so the tray and top bar are gone, the cover starts at the top of the list`() {
        assertEquals(
            Box(0, 128, 1440, 2884),
            CoverGeometry.between(listOf(null, null, listTop), tabBar, minHeight),
        )
    }

    @Test
    fun `an edge too close to the tab bar is skipped`() {
        assertEquals(
            Box(0, 338, 1440, 2884),
            CoverGeometry.between(listOf(2800, actionBarBottom), tabBar, minHeight),
        )
    }

    private val topBar = Box(0, 128, 1440, 338)
    private val slop = 24

    @Test
    fun `Home at rest is not scrolled, and neither is pull-to-refresh`() {
        assertFalse(CoverGeometry.isFeedScrolledUp(Box(0, 338, 1440, 803), topBar, slop))
        assertFalse(CoverGeometry.isFeedScrolledUp(Box(0, 608, 1440, 1073), topBar, slop))
    }

    /** Positions measured 14 Nov while the Stories row was dragged up. */
    @Test
    fun `dragging the Stories row up counts as scrolled`() {
        assertTrue(CoverGeometry.isFeedScrolledUp(Box(0, 128, 1440, 536), topBar, slop))
        assertTrue(CoverGeometry.isFeedScrolledUp(null, topBar, slop))
        assertTrue(CoverGeometry.isFeedScrolledUp(Box(0, 128, 1440, 536), null, slop))
    }

    @Test
    fun `no tab bar means no cover, so the tabs can never be covered`() {
        assertNull(CoverGeometry.between(listOf(trayBottom), null, minHeight))
    }

    @Test
    fun `no usable edge means no cover rather than a full-screen one`() {
        assertNull(CoverGeometry.between(listOf(null, null), tabBar, minHeight))
        assertNull(CoverGeometry.between(emptyList(), tabBar, minHeight))
        assertNull(CoverGeometry.between(listOf(2700), tabBar, minHeight))
    }
}
